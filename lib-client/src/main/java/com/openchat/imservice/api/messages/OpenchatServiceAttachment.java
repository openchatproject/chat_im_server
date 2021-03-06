package com.openchat.imservice.api.messages;

import com.openchat.protocal.util.guava.Optional;

import java.io.InputStream;

public abstract class OpenchatServiceAttachment {

  private final String contentType;

  protected OpenchatServiceAttachment(String contentType) {
    this.contentType = contentType;
  }

  public String getContentType() {
    return contentType;
  }

  public abstract boolean isStream();
  public abstract boolean isPointer();

  public OpenchatServiceAttachmentStream asStream() {
    return (OpenchatServiceAttachmentStream)this;
  }

  public OpenchatServiceAttachmentPointer asPointer() {
    return (OpenchatServiceAttachmentPointer)this;
  }

  public static Builder newStreamBuilder() {
    return new Builder();
  }

  public static class Builder {

    private InputStream      inputStream;
    private String           contentType;
    private String           fileName;
    private long             length;
    private ProgressListener listener;
    private boolean          voiceNote;
    private int              width;
    private int              height;

    private Builder() {}

    public Builder withStream(InputStream inputStream) {
      this.inputStream = inputStream;
      return this;
    }

    public Builder withContentType(String contentType) {
      this.contentType = contentType;
      return this;
    }

    public Builder withLength(long length) {
      this.length = length;
      return this;
    }

    public Builder withFileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public Builder withListener(ProgressListener listener) {
      this.listener = listener;
      return this;
    }

    public Builder withVoiceNote(boolean voiceNote) {
      this.voiceNote = voiceNote;
      return this;
    }

    public Builder withWidth(int width) {
      this.width = width;
      return this;
    }

    public Builder withHeight(int height) {
      this.height = height;
      return this;
    }

    public OpenchatServiceAttachmentStream build() {
      if (inputStream == null) throw new IllegalArgumentException("Must specify stream!");
      if (contentType == null) throw new IllegalArgumentException("No content type specified!");
      if (length == 0)         throw new IllegalArgumentException("No length specified!");

      return new OpenchatServiceAttachmentStream(inputStream, contentType, length, Optional.fromNullable(fileName), voiceNote, Optional.<byte[]>absent(), width, height, listener);
    }
  }

  
  public interface ProgressListener {
    
    public void onAttachmentProgress(long total, long progress);
  }
}
