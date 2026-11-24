package com.jornal.console;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consola del administrador de la región: tablero, salud de los servicios y colas. */
@RestController
@RequestMapping("/admin/console")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConsoleController {

    private final ConsoleService console;

    public AdminConsoleController(ConsoleService console) {
        this.console = console;
    }

    /** Todo junto: tablero, servicios (los caídos primero, con {@code down}) y colas. */
    @GetMapping
    public Map<String, Object> all() {
        List<ConsoleService.ServiceHealth> services = console.services();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dashboard", console.dashboard());
        out.put("down", services.stream().filter(s -> !"UP".equals(s.status())).map(ConsoleService.ServiceHealth::name).toList());
        out.put("services", services);
        out.put("queues", console.queues());
        return out;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return console.dashboard();
    }

    @GetMapping("/services")
    public List<ConsoleService.ServiceHealth> services() {
        return console.services();
    }

    @GetMapping("/queues")
    public Map<String, Object> queues() {
        return console.queues();
    }
}
