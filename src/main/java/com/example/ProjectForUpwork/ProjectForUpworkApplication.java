package com.example.ProjectForUpwork;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class ProjectForUpworkApplication extends SpringBootServletInitializer{

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
      return application.sources(ProjectForUpworkApplication.class);
    }
    
    public static void main(String[] args) {
	SpringApplication.run(ProjectForUpworkApplication.class, args);
    }

}
