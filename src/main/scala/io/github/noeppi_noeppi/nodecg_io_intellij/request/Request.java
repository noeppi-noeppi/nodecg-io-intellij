package io.github.noeppi_noeppi.nodecg_io_intellij.request;

import com.google.gson.JsonObject;

public class Request {

    public String method;
    public JsonObject data;

    public Request() {

    }

    public Request(String method, JsonObject data) {
        this.method = method;
        this.data = data;
    }
}
