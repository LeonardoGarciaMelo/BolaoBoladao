package br.com.bolaoboladao.partidas.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

@ApplicationScoped
public class TimeConfiguration {
    @Produces
    @ApplicationScoped
    Clock clock() {
        return Clock.systemUTC();
    }
}
