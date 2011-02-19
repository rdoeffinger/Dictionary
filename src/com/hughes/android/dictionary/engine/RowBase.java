package com.hughes.android.dictionary.engine;

import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;

import com.hughes.util.IndexedObject;
import com.hughes.util.raf.RAFListSerializer;

public abstract class RowBase extends IndexedObject {
  /**
   * the Index owning this RowBase.
   */
  public final Index index;
  
  /**
   * Where this RowBase points to.
   */
  public final int referenceIndex;

  /**
   * the TokenRow above this RowBase, populated on demand.
   */
  private TokenRow tokenRow = null;
  
  RowBase(final RandomAccessFile raf, final int thisRowIndex, final Index index) throws IOException {
    super(thisRowIndex);
    this.index = index;
    this.referenceIndex = raf.readInt();  // what this points to.
  }

  public RowBase(final int referenceIndex, final int thisRowIndex, final Index index) {
    super(thisRowIndex);
    this.index = index;
    this.referenceIndex = referenceIndex;
  }

  /**
   * @return the TokenRow that this row is "filed under".
   */
  public TokenRow getTokenRow(final boolean search) {
    if (tokenRow == null && search) {
      int r = index() - 1;
      int rUp = index() + 1;
      while (r >= 0) {
        final RowBase row = index.rows.get(r);
        final TokenRow candidate = row.getTokenRow(false);
        if (candidate != null) {
          for (++r; r <= index(); ++r) {
            index.rows.get(r).setTokenRow(candidate);
          }
          break;
        }
        if (rUp < index.rows.size()) {
          final RowBase rowUp = index.rows.get(rUp);
          TokenRow candidateUp = rowUp.getTokenRow(false);
          if (candidateUp != null) {
            // Did we hit the next set of TokenRows?
            if (candidateUp.index() > this.index()) {  
              final int tokenIndex = index.sortedIndexEntries.get(candidateUp.referenceIndex - 1).startRow;
              candidateUp = (TokenRow) index.rows.get(tokenIndex);
            }
            for (--rUp; rUp >= index(); --rUp) {
              index.rows.get(rUp).setTokenRow(candidateUp);
            }
            break;
          }
          rUp++;
        }
        --r;
      }
      assert tokenRow != null;
    }
    return tokenRow;
  }
  
  public void setTokenRow(TokenRow tokenRow) {
    assert this.tokenRow == null;
    assert tokenRow != null;
    this.tokenRow = tokenRow;
  }

  public abstract void print(PrintStream out);

  public abstract String getRawText(final boolean compact);

  // RowBase must manage "disk-based" polymorphism.  All other polymorphism is
  // dealt with in the normal manner.
  static class Serializer implements RAFListSerializer<RowBase> {
    
    final Index index;
    
    Serializer(final Index index) {
      this.index = index;
    }

    @Override
    public RowBase read(RandomAccessFile raf, final int listIndex) throws IOException {
      final byte rowType = raf.readByte();
      if (rowType == 0) {
        return new PairEntry.Row(raf, listIndex, index);
      } else if (rowType == 1) {
        return new TokenRow(raf, listIndex, index);
      }
      throw new RuntimeException("Invalid rowType:" + rowType);
    }

    @Override
    public void write(RandomAccessFile raf, RowBase t) throws IOException {
      if (t instanceof PairEntry.Row) {
        raf.writeByte(0);
      } else if (t instanceof TokenRow) {
        raf.writeByte(1);
      }
      raf.writeInt(t.referenceIndex);
    }
  }

}
