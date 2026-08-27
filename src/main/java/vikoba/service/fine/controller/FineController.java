package vikoba.service.fine.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.fine.dto.*;
import vikoba.service.fine.service.FineService;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fines")
public class FineController {
    private final FineService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FineResponse>>> list(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Fines retrieved.", service.list(groupId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FineResponse>> create(@RequestParam Long groupId, @RequestBody FineInput input) {
        return ResponseEntity.ok(ApiResponse.success("Fine issued.", service.create(groupId, input)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FineResponse>> update(@PathVariable Long id, @RequestParam Long groupId,
            @RequestBody FineInput input) {
        return ResponseEntity.ok(ApiResponse.success("Fine updated.", service.update(groupId, id, input)));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<FineTypeResponse>>> types(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Fine types retrieved.", service.types(groupId)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> validation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }
}
