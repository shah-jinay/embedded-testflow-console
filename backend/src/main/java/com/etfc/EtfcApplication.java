package com.etfc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class EtfcApplication {

  public static void main(String[] args) {
    SpringApplication.run(EtfcApplication.class, args);
  }
}
