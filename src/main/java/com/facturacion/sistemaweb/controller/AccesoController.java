package com.facturacion.sistemaweb.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class AccesoController {
    @GetMapping("/login") public String login() { return "login"; }
    @GetMapping("/sin-permiso") @ResponseStatus(HttpStatus.FORBIDDEN)
    public String denegado() { return "sin-permiso"; }
}
