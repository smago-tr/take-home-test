package com.takehome.forms.providers;

public record EmailRequest(String to, String from, String subject, String body) {
}
