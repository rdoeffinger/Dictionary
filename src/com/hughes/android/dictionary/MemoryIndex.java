package com.hughes.android.dictionary;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;


public final class MemoryIndex {
  
  private static final long serialVersionUID = 3375180767865334065L;

  static final class Node implements Serializable {

    private static final long serialVersionUID = 8824115665859184225L;

    final String[] chars;
    final Node[] children;
    final int[] offsets;
    
    Node(final int numChildren, final int numOffsets) {
      chars = new String[numChildren];
      children = new Node[numChildren];
      offsets = new int[numOffsets];
    }
    
    int descendantCount() {
      int total = 1;
      for (final Node child : children) {
        total += child.descendantCount();
      }
      return total;
    }
    
    Node(final DataInputStream is) throws IOException {
      final int numChildren = is.readInt();
      chars = new String[numChildren];
      children = new Node[numChildren];
      for (int i = 0; i < numChildren; ++i) {
        chars[i] = is.readUTF().intern();
        children[i] = new Node(is);
      }
      final int numOffsets = is.readInt();
      offsets = new int[numOffsets];
      for (int i = 0; i < numOffsets; ++i) {
        offsets[i] = is.readInt();
      }
    }
    
    void write(final DataOutputStream os) throws IOException {
      os.writeInt(chars.length);
      for (int i = 0; i < chars.length; i++) {
        os.writeUTF(chars[i]);
        children[i].write(os);
      }
      os.writeInt(offsets.length);
      for (int i = 0; i < offsets.length; i++) {
        os.writeInt(offsets[i]);
      }
    }
  }
  
}
