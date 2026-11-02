package lux.dartgame.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** Target progress and equal-round resolution; positions are the stable leg throw order. */
@Data
public class TargetGameState {
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

    public TargetGameState copy() {
        TargetGameState copy = new TargetGameState();
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
