package com.tencent.trtcasr.common;

/** An error returned by the TRTC-ASR service or the SDK itself. */
public class ASRException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int code;

    public ASRException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ASRException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return "trtc-asr error [" + code + "]: " + super.getMessage();
    }

    /** Raw message without the "trtc-asr error [code]:" prefix. */
    public String getRawMessage() {
        return super.getMessage();
    }

    public static ASRException invalidParam(String message) {
        return new ASRException(ErrorCodes.INVALID_PARAM, message);
    }

    public static ASRException invalidParam(String format, Object... args) {
        return new ASRException(ErrorCodes.INVALID_PARAM, String.format(format, args));
    }
}
