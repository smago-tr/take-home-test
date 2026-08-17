package com.takehome.forms.providers;

public record HttpResponse<T>(int statusCode, T body) {
}
