package com.devup.qcm.survey.process;

import com.devup.qcm.survey.entities.PushOrder;

public class PushError extends Exception implements PushResponse {
    private final PushOrder content;
    PushOrder order;
    int code = CODE_DEFAULT_FAILD;

    public PushError(String message, int code, PushOrder content) {
        this(null, message, code, content);
    }

    public PushError(Throwable cause, String message, int code, PushOrder content) {
        super(message, cause);
        this.code = code;
        this.content = content;
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }

    @Override
    public int getCode() {
        return this.code;
    }

    @Override
    public PushOrder getContent() {
        return order;
    }
}
