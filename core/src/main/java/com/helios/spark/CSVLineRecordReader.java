package com.helios.spark;

import jdk.internal.joptsimple.internal.Strings;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.Seekable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.*;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.*;

import java.io.IOException;
import java.util.*;

@InterfaceAudience.LimitedPrivate({"MapReduce", "Pig"})
@InterfaceStability.Evolving
public class CSVLineRecordReader extends RecordReader<LongWritable, Text> {
  private static final Log LOG = LogFactory.getLog(CSVLineRecordReader.class);
  public static final String MAX_LINE_LENGTH = "mapreduce.input.linerecordreader.line.maxlength";
  private long start;
  private long pos;
  private long end;
  private SplitLineReader in;
  private FSDataInputStream fileIn;
  private Seekable filePosition;
  private int maxLineLength;
  private LongWritable key;
  private Text value;
  private boolean isCompressedInput;
  private Decompressor decompressor;
  private byte[] recordDelimiterBytes;

  private String[] columnNames;

  public CSVLineRecordReader() {}

  public CSVLineRecordReader(byte[] recordDelimiter) {
    this.recordDelimiterBytes = recordDelimiter;
  }

  public void initialize(InputSplit genericSplit, TaskAttemptContext context) throws IOException {
    FileSplit split = (FileSplit) genericSplit;
    Configuration job = context.getConfiguration();
    this.maxLineLength =
            job.getInt("mapreduce.input.linerecordreader.line.maxlength", Integer.MAX_VALUE);
    this.start = split.getStart();
    this.end = this.start + split.getLength();
    Path file = split.getPath();
    FileSystem fs = file.getFileSystem(job);
    this.fileIn = fs.open(file);
    CompressionCodec codec = (new CompressionCodecFactory(job)).getCodec(file);
    if (null != codec) {
      this.isCompressedInput = true;
      this.decompressor = CodecPool.getDecompressor(codec);
      if (codec instanceof SplittableCompressionCodec) {
        SplitCompressionInputStream cIn =
                ((SplittableCompressionCodec) codec)
                        .createInputStream(
                                this.fileIn,
                                this.decompressor,
                                this.start,
                                this.end,
                                SplittableCompressionCodec.READ_MODE.BYBLOCK);
        this.in = new CompressedSplitLineReader(cIn, job, this.recordDelimiterBytes);
        this.start = cIn.getAdjustedStart();
        this.end = cIn.getAdjustedEnd();
        this.filePosition = cIn;
      } else {
        this.in =
                new SplitLineReader(
                        codec.createInputStream(this.fileIn, this.decompressor),
                        job,
                        this.recordDelimiterBytes);
        this.filePosition = this.fileIn;
      }
    } else {
      this.fileIn.seek(this.start);
      this.in =
              new UncompressedSplitLineReader(
                      this.fileIn, job, this.recordDelimiterBytes, split.getLength());
      this.filePosition = this.fileIn;
    }

    if (this.start != 0L) {
      this.start += (long) this.in.readLine(new Text(), 0, this.maxBytesToConsume(this.start));
    }

    this.pos = this.start;

    String csvHeaderStr = context.getConfiguration().get("csvHeaderStr");
    this.columnNames = csvHeaderStr.split(",");
  }

  private int maxBytesToConsume(long pos) {
    return this.isCompressedInput
            ? Integer.MAX_VALUE
            : (int) Math.max(Math.min(2147483647L, this.end - pos), (long) this.maxLineLength);
  }

  private long getFilePosition() throws IOException {
    long retVal;
    if (this.isCompressedInput && null != this.filePosition) {
      retVal = this.filePosition.getPos();
    } else {
      retVal = this.pos;
    }

    return retVal;
  }

  private int skipUtfByteOrderMark() throws IOException {
    int newMaxLineLength = (int) Math.min(3L + (long) this.maxLineLength, 2147483647L);
    int newSize = this.in.readLine(this.value, newMaxLineLength, this.maxBytesToConsume(this.pos));
    this.pos += (long) newSize;
    int textLength = this.value.getLength();
    byte[] textBytes = this.value.getBytes();
    if (textLength >= 3 && textBytes[0] == -17 && textBytes[1] == -69 && textBytes[2] == -65) {
      LOG.info("Found UTF-8 BOM and skipped it");
      textLength -= 3;
      newSize -= 3;
      if (textLength > 0) {
        textBytes = this.value.copyBytes();
        this.value.set(textBytes, 3, textLength);
      } else {
        this.value.clear();
      }
    }

    return newSize;
  }

  public boolean nextKeyValue() throws IOException {
    if (this.key == null) {
      this.key = new LongWritable();
    }

    this.key.set(this.pos);
    if (this.value == null) {
      this.value = new Text();
    }

    int newSize = 0;

    while (this.getFilePosition() <= this.end || this.in.needAdditionalRecordAfterSplit()) {
      if (this.pos == 0L) {
        newSize = this.skipUtfByteOrderMark();
      } else {
        newSize =
                this.in.readLine(this.value, this.maxLineLength, this.maxBytesToConsume(this.pos));
        this.pos += (long) newSize;
      }

      if (newSize == 0 || newSize < this.maxLineLength) {
        break;
      }

      LOG.info("Skipped line of size " + newSize + " at pos " + (this.pos - (long) newSize));
    }

    if (newSize == 0) {
      this.key = null;
      this.value = null;
      return false;
    } else {
      this.value = modifyText(this.value);
      return true;
    }
  }

  private XXX xxx = getColumnIndicesToAnonymizeAndRemove();

  private Text modifyText(Text text) {
    String delimiter = ",";

    LOG.info("====> modifyText");
    LOG.info(Arrays.toString(this.columnNames));

    System.out.println("modifyText");
    System.out.printf("modifyText: before: %s \n", text);
    System.out.printf("columnNames: %s \n", Arrays.toString(this.columnNames));
    String str = text.toString();
    String[] tokens = str.split(delimiter);
    List<String> newTokens = new ArrayList<>(tokens.length);
    for (int i = 0; i < tokens.length; i++) {
      if (xxx.indicesToRemove.contains(i)) {
        continue;
      }

      String token = tokens[i];
      if (xxx.indicesToAnonymize.contains(i)) {
        token = String.format("%s_anony", token);
      }
      newTokens.add(token);
    }
    String newStr = String.join(delimiter, newTokens);
    Text modifiedText = new Text(newStr);
    System.out.printf("modifyText: after: %s \n", modifiedText);
    return modifiedText;
  }

  class XXX {
    private Set<Integer> indicesToAnonymize;
    private Set<Integer> indicesToRemove;

    public XXX(Set<Integer> indicesToAnonymize, Set<Integer> indicesToRemove) {
      this.indicesToAnonymize = indicesToAnonymize;
      this.indicesToRemove = indicesToRemove;
    }
  }

  private XXX getColumnIndicesToAnonymizeAndRemove() {
    // TODO: fix
    Set<Integer> a = new HashSet<>();
    a.add(0);
    Set<Integer> b = new HashSet<>();
    b.add(1);
    return new XXX(a, b);
  }

  public LongWritable getCurrentKey() {
    return this.key;
  }

  public Text getCurrentValue() {
    return this.value;
  }

  public float getProgress() throws IOException {
    return this.start == this.end
            ? 0.0F
            : Math.min(
            1.0F, (float) (this.getFilePosition() - this.start) / (float) (this.end - this.start));
  }

  public synchronized void close() throws IOException {
    try {
      if (this.in != null) {
        this.in.close();
      }
    } finally {
      if (this.decompressor != null) {
        CodecPool.returnDecompressor(this.decompressor);
      }
    }
  }
}