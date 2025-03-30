package common.message;

public enum MessageType {
    REQUEST(0), RESPONSE(1);
    int code;

    MessageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
