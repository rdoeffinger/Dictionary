package com.hughes.android.dictionary;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.TreeMap;

import com.hughes.util.LRUCacheMap;


public final class Index {
  
  final String filename;
  final RandomAccessFile file;
  
  final Node root;
  final Map<Integer,Node> locationToNode = new LRUCacheMap<Integer,Node>(5000);
  
  
  public Index(final String filename) throws IOException {
    this.filename = filename;
    file = new RandomAccessFile(filename, "r");
    root = getNode("", 0);
  }
  
  public Node lookup(final String text) throws IOException {
    return lookup(text, 0, root);
  }
  
  private Node lookup(final String text, final int pos, final Node n) throws IOException {
    if (pos == text.length()) {
      return n;
    }
    for (int i = pos + 1; i <= text.length(); ++i) {
      final Integer child = n.children.get(text.substring(pos, i));
      if (child != null) {
        return lookup(text, i, getNode(text.substring(0, i), child));
      }
    }
    return n;
  }
  
  private Node getNode(final String text, final int location) throws IOException {
    Node node = locationToNode.get(location);
    if (node == null) {
      node = new Node(text, location);
      locationToNode.put(location, node);
    }
    return node;
  }

  final class Node {
    final String text;
    final int location;
    final TreeMap<String,Integer> children;
    final int[] offsets;
    
    Node(final String text, final int location) throws IOException {
      this.text = text;
      this.location = location;
      
      file.seek(location);
      final int numChildren = file.readInt();
      children = new TreeMap<String, Integer>();
      for (int i = 0; i < numChildren; ++i) {
        final String chunk = file.readUTF().intern();
        if (chunk.length() == 0) {
          throw new IOException("Empty string chunk.");
        }
        children.put(chunk, file.readInt());
      }
      
      final int numOffsets = file.readInt();
      offsets = new int[numOffsets];
      for (int i = 0; i < offsets.length; ++i) {
        offsets[i] = file.readInt();
      }
    }
  }

}
