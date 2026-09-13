package com.rideshare.authservice.exception;

import java.lang.reflect.InaccessibleObjectException;

public class InvalidCredentialException extends RuntimeException{

    public InvalidCredentialException(String message){
        super(message);
    }
}
