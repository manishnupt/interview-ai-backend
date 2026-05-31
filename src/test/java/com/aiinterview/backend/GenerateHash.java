package com.aiinterview.backend;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(10);
        System.out.println("HASH:" + enc.encode("password"));
    }
}
