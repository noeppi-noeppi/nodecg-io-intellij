package io.github.noeppi_noeppi.nodecg_io_intellij.request;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class Response {

    public boolean success;
    public String message;
    public JsonElement data;

    public Response(boolean success, String message, JsonElement data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public Response(boolean success, String message, String data) {
        this.success = success;
        this.message = message;
        this.data = data == null ? JsonNull.INSTANCE : new JsonPrimitive(data);
    }

    public Response(boolean success, String message, Number data) {
        this.success = success;
        this.message = message;
        this.data = data == null ? JsonNull.INSTANCE : new JsonPrimitive(data);
    }

    public Response(boolean success, String message, boolean data) {
        this.success = success;
        this.message = message;
        this.data = new JsonPrimitive(data);
    }

    public Response(boolean success, String message, char data) {
        this.success = success;
        this.message = message;
        this.data = new JsonPrimitive(data);
    }

    public Response(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.data = new JsonObject();
    }
}