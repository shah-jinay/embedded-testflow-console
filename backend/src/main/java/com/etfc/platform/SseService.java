package com.etfc.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Slf4j
public class SseService {

  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
  private final ObjectMapper mapper;

  public SseService(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(300_000L);
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(ex -> emitters.remove(emitter));

    try {
      emitter.send(SseEmitter.event().name("connected").data("{}"));
    } catch (IOException e) {
      emitters.remove(emitter);
    }

    log.debug("SSE client subscribed; active emitters: {}", emitters.size());
    return emitter;
  }

  public void broadcast(RunEvent event) {
    if (emitters.isEmpty()) return;

    String data;
    try {
      data = mapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize SSE event", e);
      return;
    }

    List<SseEmitter> dead = new ArrayList<>();
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name(event.type()).data(data));
      } catch (Exception e) {
        dead.add(emitter);
      }
    }
    if (!dead.isEmpty()) {
      emitters.removeAll(dead);
      log.debug("Removed {} stale SSE emitters", dead.size());
    }
  }
}
