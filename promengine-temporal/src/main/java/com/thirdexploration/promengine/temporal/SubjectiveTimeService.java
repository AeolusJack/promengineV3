package com.thirdexploration.promengine.temporal;

import com.thirdexploration.promengine.memory.model.MemoryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SubjectiveTimeService {

    private final TimeDilationCalculator dilationCalculator;
    private final EventDensityTracker densityTracker;

    /**
     * 计算记忆的主观年龄描述
     */
    public String getSubjectiveAgeDescription(MemoryEntry entry) {
        Instant now = Instant.now();
        Duration realDuration = Duration.between(entry.getTimestamp(), now);
        double dilationFactor = dilationCalculator.getCurrentFactor();
        Duration subjectiveDuration = realDuration.multipliedBy((long) (dilationFactor * 1000)).dividedBy(1000);

        if (subjectiveDuration.toDays() < 1) {
            return "刚刚";
        } else if (subjectiveDuration.toDays() < 7) {
            return "前几天";
        } else if (subjectiveDuration.toDays() < 30) {
            return "几周前";
        } else {
            return "很久以前";
        }
    }

    public double getDilationFactor() {
        return dilationCalculator.getCurrentFactor();
    }

    public void recordEvent() {
        densityTracker.recordEvent();
        dilationCalculator.update(densityTracker.getDensity());
    }
}