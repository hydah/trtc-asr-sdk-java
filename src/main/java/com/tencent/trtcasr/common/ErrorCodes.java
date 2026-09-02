package com.tencent.trtcasr.common;

/** Error codes for the TRTC-ASR SDK. */
public final class ErrorCodes {
    public static final int INVALID_PARAM = 1001;
    public static final int CONNECT_FAILED = 1002;
    public static final int WRITE_FAILED = 1003;
    public static final int READ_FAILED = 1004;
    public static final int AUTH_FAILED = 1005;
    public static final int TIMEOUT = 1006;
    public static final int SERVER_ERROR = 1007;
    public static final int ALREADY_STARTED = 1008;
    public static final int NOT_STARTED = 1009;
    public static final int ALREADY_STOPPED = 1010;

    private ErrorCodes() {
    }
}
