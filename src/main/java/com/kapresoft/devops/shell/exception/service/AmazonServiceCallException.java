package com.kapresoft.devops.shell.exception.service;

import com.amazonaws.AmazonServiceException;

import org.springframework.lang.NonNull;

@SuppressWarnings("unused")
public class AmazonServiceCallException extends ServiceException {

    public AmazonServiceCallException(@NonNull String msg, AmazonServiceException awsClientException) {
        super(msg, awsClientException);
    }

}
