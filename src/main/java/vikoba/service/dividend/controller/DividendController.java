package vikoba.service.dividend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.dividend.dto.*;
import vikoba.service.dividend.service.DividendService;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dividends")
public class DividendController {
    private final DividendService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DividendResponse>>> list(@RequestParam Long groupId,
            @RequestParam Integer year) {
        return ResponseEntity.ok(ApiResponse.success("Dividends retrieved.", service.list(groupId, year)));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<List<DividendResponse>>> generate(@RequestParam Long groupId,
            @RequestBody DividendInput input) {
        return ResponseEntity.ok(ApiResponse.success("Dividends generated.", service.generate(groupId, input)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> error(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
}
