package com.rideshare.authservice.exception;

public class UserDisabledException extends RuntimeException{

    public UserDisabledException(String message){
        super(message);
    }
}
