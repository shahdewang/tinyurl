package com.bufferstack.tinyurl.controller;

import com.bufferstack.tinyurl.models.TinyUrlMapping;
import com.bufferstack.tinyurl.service.UrlMappingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/tinyurl")
public class TinyUrlController {

    private final UrlMappingService urlMappingService;

    public TinyUrlController(UrlMappingService urlMappingService) {
        this.urlMappingService = urlMappingService;
    }

    @PostMapping
    public TinyUrlMapping addLink(@RequestBody String fullUrl) {
        return urlMappingService.addLink(fullUrl);
    }

    @GetMapping(path = "/{code}", produces = APPLICATION_JSON_VALUE)
    public TinyUrlMapping getLink(@PathVariable String code) {
        return urlMappingService.getLink(code);
    }
}
