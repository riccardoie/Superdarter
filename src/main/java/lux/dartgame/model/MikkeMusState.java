package lux.dartgame.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** Persisted per leg; player positions are the stable throw order within that leg. */
@Data
public class MikkeMusState {
    private String phase = "PLAY";
    private int currentPosition;
    private Map<Integer, Map<String, Integer>> marks = new LinkedHashMap<>();
    private List<Integer> finishers = new ArrayList<>();
    private List<Integer> redemption = new ArrayList<>();
    private List<Integer> bullCandidates = new ArrayList<>();
    private Map<Integer, Integer> bullThrows = new LinkedHashMap<>();
    private List<BullThrow> bullHistory = new ArrayList<>();
    private int bullAttempt = 1;

    public record BullThrow(int position, int region, int attempt) {
    }

    public MikkeMusState copy() {
        MikkeMusState copy = new MikkeMusState();
        copy.phase = phase;
        copy.currentPosition = currentPosition;
        marks.forEach((position, hits) -> copy.marks.put(position, new LinkedHashMap<>(hits)));
        copy.finishers.addAll(finishers);
        copy.redemption.addAll(redemption);
        copy.bullCandidates.addAll(bullCandidates);
        copy.bullThrows.putAll(bullThrows);
        copy.bullHistory.addAll(bullHistory);
        copy.bullAttempt = bullAttempt;
        return copy;
    }
}
