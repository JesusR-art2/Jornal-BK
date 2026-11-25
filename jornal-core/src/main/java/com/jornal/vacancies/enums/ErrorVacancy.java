package com.jornal.vacancies.enums;

import com.jornal.shared.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ErrorVacancy implements ErrorCode {

    VAC001_NOT_FOUND(HttpStatus.NOT_FOUND, "Vacancy %s does not exist"),
    VAC002_INVALID_SCHEDULE(HttpStatus.BAD_REQUEST, "The vacancy must start in the future and end after it starts"),
    VAC003_TIMEZONE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Time zone %s is not valid for this country"),
    VAC004_NOT_ALLOWED(HttpStatus.FORBIDDEN, "You cannot perform this action on the vacancy"),
    VAC005_NOT_OPEN(HttpStatus.CONFLICT, "The vacancy is not receiving applications"),
    VAC006_ALREADY_APPLIED(HttpStatus.CONFLICT, "You already applied to this vacancy"),
    VAC007_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Application %s does not exist"),
    VAC008_INVALID_TRANSITION(HttpStatus.CONFLICT, "The application cannot change from %s to %s"),
    VAC009_NO_SLOTS(HttpStatus.CONFLICT, "The vacancy needs %s more people"),
    VAC010_NOT_ENDED(HttpStatus.CONFLICT, "The work has not ended yet"),
    VAC011_ALREADY_STARTED(HttpStatus.CONFLICT, "The work already started"),
    VAC012_NOT_WORKER(HttpStatus.FORBIDDEN, "Only workers can apply to vacancies"),
    VAC013_ALREADY_PAID(HttpStatus.CONFLICT, "The payment is not pending"),
    VAC014_JUSTIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "Paying less than agreed (%s) requires a justification"),
    VAC015_AUTO_PAYOUT_IN_PROGRESS(HttpStatus.CONFLICT, "The automatic payment is in progress");

    private final HttpStatus status;
    private final String message;

    ErrorVacancy(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
