package com.avex.vol1.controller;

import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.avex.vol1.model.PartResult;
import com.avex.vol1.service.SearchService;
import org.apache.poi.util.IOUtils;
@Controller
public class ExelController {

    private final SearchService exelService;

    public ExelController(SearchService exelService) {
        this.exelService = exelService;
    }

    @GetMapping({"/", "/search", ""})
    public String showSearchForm(Model model) {
        model.addAttribute("partNumber", "");
        return "search";
    }

    @PostMapping("/search")
    public String performSearch(@RequestParam("partNumber") String partNumber, Model model) {
        model.addAttribute("partNumber", partNumber);

        if (partNumber == null || partNumber.trim().length() < 3) {
            model.addAttribute("error", "Введите номер запчасти (минимум 3 символа)");
            return "search";
        }

        try {
            List<PartResult> results = exelService.search(partNumber.trim());
            model.addAttribute("results", results);
            model.addAttribute("searched", true);
        } catch (Exception e) {
            model.addAttribute("error", "Ошибка при поиске: " + e.getMessage());
        }

        return "search";
    }
   
}