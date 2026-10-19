package com.example;

public class MessageApp {

    public static void main(String[] args) {
        MessageService service = new MessageService("Hi");
        System.out.println(service.getPrefix());
        Person person = new Person(null, 0);
        if (person.isAdult()) {
            System.out.println("Person is an adult.");
        }
        System.out.println(service.formatMsg("World"));
    }
}
