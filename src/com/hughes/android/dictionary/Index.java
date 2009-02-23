package com.hughes.android.dictionary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.hughes.util.LRUCacheMap;


public final class Index {
  
  final String filename;
  final RandomAccessFile file;
  
  final Node root;
  final Map<Integer,Node> indexOffsetToNode = new LRUCacheMap<Integer,Node>(5000);
  
  
  public Index(final String filename) throws IOException {
    this.filename = filename;
    file = new RandomAccessFile(filename, "r");
    root = getNode(new NodeHandle("", 0));
  }
  
  public Node lookup(final String normalizedToken) throws IOException {
    return lookup(normalizedToken, 0, root);
  }
  
  private Node lookup(final String normalizedToken, final int pos, final Node node) throws IOException {
    if (pos == normalizedToken.length()) {
      return node;
    }
    
    // Check whether any prefix of the token is a child.
    for (int i = pos + 1; i <= normalizedToken.length(); ++i) {
      final NodeHandle childHandle = node.children.get(normalizedToken.substring(pos, i));
      if (childHandle != null) {
        return lookup(normalizedToken, i, childHandle.getNode());
      }
    }
    
    // Check whether any child starts with what's left of text.
    final String remainder = normalizedToken.substring(pos);
    for (final Map.Entry<String, NodeHandle> childHandle : node.children.entrySet()) {
      if (childHandle.getKey().startsWith(remainder)) {
        return getNode(childHandle.getValue());
      }
    }
    
    return node;
  }
  
  private Node getNode(final NodeHandle nodeHandle) throws IOException {
    Node node = indexOffsetToNode.get(nodeHandle.indexOffset);
    if (node == null) {
      node = new Node(nodeHandle);
      indexOffsetToNode.put(nodeHandle.indexOffset, node);
    }
    return node;
  }
  
  final class NodeHandle {
    final String normalizedToken;
    final int indexOffset;

    NodeHandle(final String normalizedToken, final int indexOffset) throws IOException {
      this.normalizedToken = normalizedToken;
      this.indexOffset = indexOffset;
    }
    
    Node getNode() throws IOException {
      return Index.this.getNode(this);
    }
  }

  final class Node {
    final NodeHandle nodeHandle;
    final TreeMap<String,NodeHandle> children;
    final Map<String,int[]> tokenToOffsets = new TreeMap<String, int[]>(EntryFactory.entryFactory.getEntryComparator());
    final int descendantTokenCount;
    final int descendantEntryCount;
    
    Node(final NodeHandle nodeHandle) throws IOException {
      this.nodeHandle = nodeHandle;
      
      file.seek(nodeHandle.indexOffset);
      
      // Read children to offset.
      final int numChildren = file.readInt();
      children = new TreeMap<String, NodeHandle>();
      for (int i = 0; i < numChildren; ++i) {
        final String chunk = file.readUTF().intern();
        if (chunk.length() == 0) {
          throw new IOException("Empty string chunk.");
        }
        children.put(chunk, new NodeHandle(nodeHandle.normalizedToken + chunk, file.readInt()));
      }
    
      // Read tokens.
      final int numTokens = file.readInt();
      for (int i = 0; i < numTokens; ++i) {
        final String token = file.readUTF();
        assert EntryFactory.entryFactory.normalizeToken(token).equals(nodeHandle.normalizedToken);
        final int[] offsets = new int[file.readInt()];
        for (int j = 0; j < offsets.length; ++j) {
          offsets[j]= file.readInt();
        }
        tokenToOffsets.put(token, offsets);
      }
      
      // TODO: move this up, and defer the loading of the other stuff until it's needed.
      descendantTokenCount = file.readInt();
      descendantEntryCount = file.readInt();
    }
    
    @Override
    public String toString() {
      return String.format("%s(%d,%d)", nodeHandle.normalizedToken, getThisCount(), getDescendantCount());
    }
    
    public int getDescendantCount() {
      return descendantEntryCount + descendantTokenCount;
    }
    
    public int getThisCount() {
      int count = tokenToOffsets.size();
      for (final int[] offsets : tokenToOffsets.values()) {
        count += offsets.length;
      }
      return count;
    }

    public Object getDescendant(int position) throws IOException {
      assert position < getDescendantCount(); 

//      System.out.println("getD: " + this + ", " + position);
      if (position < getThisCount()) {
        for (final Map.Entry<String, int[]> tokenEntry : tokenToOffsets.entrySet()) {
          if (position == 0) {
            return tokenEntry.getKey();
          }
          --position;
          if (position < tokenEntry.getValue().length) {
            return tokenEntry.getValue()[position];
          }
          position -= tokenEntry.getValue().length;
        }
        assert false;
      }
      position -= getThisCount();
      
      
      for (final Map.Entry<String,NodeHandle> childEntry : children.entrySet()) {
        final Node child = childEntry.getValue().getNode();
        if (position < child.getDescendantCount()) {
          return child.getDescendant(position);
        }
        position -= child.getDescendantCount();
      }
      assert false;
      return null;
    }

    public void getDescendantEntryOffsets(final Set<Integer> entryOffsets, int maxSize) throws IOException {
      for (final int[] offsets : tokenToOffsets.values()) {
        for (final int offset : offsets) {
          if (entryOffsets.size() >= maxSize) {
            return;
          }
          entryOffsets.add(offset);
        }
      }
      if (entryOffsets.size() >= maxSize) {
        return;
      }
      for (final Map.Entry<String, NodeHandle> childEntry : children.entrySet()) {
        final Node child = childEntry.getValue().getNode();
        child.getDescendantEntryOffsets(entryOffsets, maxSize);
        if (entryOffsets.size() >= maxSize) {
          return;
        }
      }
    }
  }
  
}
