package com.dasigochim.file.hwp;

import com.dasigochim.report.domain.Report;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mock-hwp")
public class MockHwpReportGenerator implements HwpReportGenerator {
    @Override
    public String generate(Report report) {
        return "/files/mock-report-" + report.getId() + ".hwp";
    }
}
