package com.avex.vol1.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.avex.vol1.service.ImportService;


@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ImportService importService;

    public AdminController(ImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/import")
    public String importPage() {
        return "admin/import";
    }

    @PostMapping("/import")
    public String runImport(Model model) {
        String log = importService.importAll();
        model.addAttribute("log", log);
        return "admin/import";
    }
}