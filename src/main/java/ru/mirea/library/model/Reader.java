package ru.mirea.library.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Reader {

    private int id;
    private String fullName;
    private String email;
    private String phone;
    private String libraryCardNumber;
    private LocalDateTime registeredAt;

    public Reader() { }

    public Reader(String fullName, String email, String phone, String libraryCardNumber) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.libraryCardNumber = libraryCardNumber;
    }

    public Reader(int id, String fullName, String email, String phone,
                  String libraryCardNumber, LocalDateTime registeredAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.libraryCardNumber = libraryCardNumber;
        this.registeredAt = registeredAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLibraryCardNumber() { return libraryCardNumber; }
    public void setLibraryCardNumber(String libraryCardNumber) {
        this.libraryCardNumber = libraryCardNumber;
    }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    @Override
    public String toString() {
        return String.format(
            "Reader{id=%d, ФИО='%s', email='%s', тел='%s', билет='%s', зарегистрирован=%s}",
            id, fullName, email, phone, libraryCardNumber, registeredAt
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reader reader)) return false;
        return id == reader.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}