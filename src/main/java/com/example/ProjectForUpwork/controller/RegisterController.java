/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.ProjectForUpwork.controller;

import com.example.ProjectForUpwork.model.User;
import com.example.ProjectForUpwork.service.EmailService;
import com.example.ProjectForUpwork.service.UserService;
import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 *
 * @author Moosa
 */
@Controller
public class RegisterController {
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    private UserService userService;
    private EmailService emailService;
    
    @Autowired
    public RegisterController(BCryptPasswordEncoder bCryptPasswordEncoder,UserService userService, EmailService emailService) {
	this.bCryptPasswordEncoder = bCryptPasswordEncoder;
	this.userService = userService;
	this.emailService = emailService;
    }

    // Return registration form template
    @RequestMapping(value="/register", method = RequestMethod.GET)
    public ModelAndView showRegistrationPage(ModelAndView modelAndView, User user){
	modelAndView.addObject("user", user);
	modelAndView.setViewName("register");
	return modelAndView;
    }
	
	// Process form input data
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public ModelAndView processRegistrationForm(@RequestParam("pdf_File") MultipartFile file, ModelAndView modelAndView, @Valid User user, BindingResult bindingResult, HttpServletRequest request) {
	
	// Lookup user in database by e-mail
	User userExists = userService.findByEmail(user.getEmail());
		
	if (userExists != null) {
            modelAndView.addObject("alreadyRegisteredMessage", "Oops!  There is already a user registered with the email provided.");
            modelAndView.setViewName("register");
            bindingResult.reject("email");
	}
			
	if (bindingResult.hasErrors()) { 
            modelAndView.setViewName("register");
	} else { 

            Path filePath = null;
            // new user so we create user and send confirmation e-mail
            try {
                
                Path root = Paths.get("uploads");
                
                try {
                    Files.createDirectory(root);
                } catch (IOException e) {
                }
                
                byte[] bytes = file.getBytes();
                filePath = root.resolve(file.getOriginalFilename());
                Files.write(filePath, bytes);

            } catch (IOException e) {
                System.out.println(" error in uploading >> " + e.getMessage());
            }
            
            // Disable user until they click on confirmation link in email
	    user.setEnabled(false);

            // Generate random 36-character string token for confirmation link
            user.setConfirmationToken(UUID.randomUUID().toString());
            
            user.setPdfFile(file.getOriginalFilename());
            
            userService.saveUser(user);
            
            String appUrl = request.getScheme() + "://" + request.getServerName();
            
            try{

                BodyPart messageBodyPart = new MimeBodyPart(); 
                messageBodyPart.setText("To confirm your e-mail address, please click the link below:\n" + appUrl + ":8080/confirm?token=" + user.getConfirmationToken());
                
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.attachFile(new File(filePath.toUri()));
                
                Multipart multipart = new MimeMultipart();
                multipart.addBodyPart(messageBodyPart);
                multipart.addBodyPart(attachmentPart);
                
                Properties prop = new Properties();
                prop.put("mail.smtp.auth", true);
                prop.put("mail.smtp.starttls.enable", "true");
                prop.put("mail.smtp.host", "smtp.gmail.com");
                prop.put("mail.smtp.port", "587");
                prop.put("mail.smtp.ssl.trust", "smtp.gmail.com");
                
                Session session = Session.getInstance(prop, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication("noreplytopos@gmail.com", "Google@123");
                    }
                });
                
                Message message = new MimeMessage(session); 
                message.setFrom(new InternetAddress("noreply@domain.com")); 
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(user.getEmail())); 
                message.setContent(multipart);
                
                Transport.send(message);

            }
            catch(IOException | MessagingException i){
                System.out.println(i.getMessage());
            }
            
            modelAndView.addObject("confirmationMessage", "A confirmation e-mail has been sent to " + user.getEmail());
            modelAndView.setViewName("register");
        }
        
	return modelAndView;	
    }

    // Process confirmation link
    @RequestMapping(value="/confirm", method = RequestMethod.GET)
    public ModelAndView confirmRegistration(ModelAndView modelAndView, @RequestParam("token") String token) {
        
        User user = userService.findByConfirmationToken(token);
	
        if (user == null) { // No token found in DB
            modelAndView.addObject("invalidToken", "Oops!  This is an invalid confirmation link.");
	} else { // Token found
            modelAndView.addObject("confirmationToken", user.getConfirmationToken());
        }
        
        modelAndView.setViewName("confirm");
	return modelAndView;
    
    }
    // Process confirmation link
    @RequestMapping(value="/confirm", method = RequestMethod.POST)
    public ModelAndView confirmRegistration(ModelAndView modelAndView, BindingResult bindingResult, @RequestParam Map<String, String> requestParams, RedirectAttributes redir) {
        
        modelAndView.setViewName("confirm");
	
        Zxcvbn passwordCheck = new Zxcvbn();
        
        Strength strength = passwordCheck.measure(requestParams.get("password"));
        
        if (strength.getScore() < 3) {
            //modelAndView.addObject("errorMessage", "Your password is too weak.  Choose a stronger one.");
            bindingResult.reject("password");
            
            redir.addFlashAttribute("errorMessage", "Your password is too weak.  Choose a stronger one.");
            
            modelAndView.setViewName("redirect:confirm?token=" + requestParams.get("token"));
            
            System.out.println(requestParams.get("token"));
            
            return modelAndView;
	
        }
	
        // Find the user associated with the reset token
	
        User user = userService.findByConfirmationToken(requestParams.get("token"));

	// Set new password
	user.setPassword(bCryptPasswordEncoder.encode(requestParams.get("password")));

	// Set user to enabled
	user.setEnabled(true);
	
	// Save user
	userService.saveUser(user);
	
	modelAndView.addObject("successMessage", "Your password has been set!");
	return modelAndView;
    
    }
	
}
