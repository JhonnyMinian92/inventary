package com.ecuapp.inventary.realtime;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardEventsController {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("serverTime", Instant.now().toString())));
        } catch (IOException | IllegalStateException ignored) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    @Scheduled(fixedDelay = 10000)
    public void publishSnapshot() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("snapshot")
                    .data(Map.of("updatedAt", Instant.now().toString())));
            } catch (IOException | IllegalStateException ignored) {
                emitters.remove(emitter);
            }
        }
    }
}
