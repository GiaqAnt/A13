package com.robotchallenge.t8.controller;

import com.robotchallenge.t8.config.CustomExecutorConfiguration;
import com.robotchallenge.t8.dto.request.OpponentCoverageRequestDTO;
import com.robotchallenge.t8.dto.request.StudentCoverageRequestDTO;
import com.robotchallenge.t8.service.CoverageService;
import com.robotchallenge.t8.util.BuildResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import testrobotchallenge.commons.models.dto.score.EvosuiteCoverageDTO;

import java.io.IOException;
import java.util.concurrent.*;

@Controller
@CrossOrigin
public class CoverageController {

    private static final Logger logger = LoggerFactory.getLogger(CoverageController.class);
    private final CustomExecutorConfiguration.CustomExecutorService compileExecutor;
    private final CoverageService coverageService;

    public CoverageController(CustomExecutorConfiguration.CustomExecutorService compileExecutor,
                              CoverageService coverageService) {
        this.compileExecutor = compileExecutor;
        this.coverageService = coverageService;
    }

    /*
     * Le eccezioni lanciate da CoverageService sono catturate e gestite da advice.TaskExceptionHandler
     */

    @Operation(
            summary = "Calculate EvoSuite coverage for an opponent",
            description = "Uploads opponent coverage request data and a project file (ZIP/JAR) to calculate EvoSuite coverage."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Coverage successfully calculated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EvosuiteCoverageDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(
            value = "/coverage/opponent",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<EvosuiteCoverageDTO> calculateRobotEvosuiteCoverage(
            @Parameter(
                    description = "Opponent coverage request payload (JSON)",
                    required = true
            )
            @RequestPart("request") OpponentCoverageRequestDTO request,

            @Parameter(
                    description = "Maven Project file ZIP to analyze",
                    required = true,
                    content = @Content(mediaType = "application/zip",
                            schema = @Schema(type = "binary"))
            )
            @RequestPart("project") MultipartFile project
    ) throws IOException, RejectedExecutionException {
        logger.info("[CoverageController] [POST /score/opponent] Ricevuta richiesta con body {} e MultiPartFile {}", request, project.getOriginalFilename());
        String result = coverageService.calculateRobotCoverage(request, project);

        EvosuiteCoverageDTO responseBody = BuildResponse.buildExtendedDTO(result);

        logger.info("[CoverageController] [POST /score/opponent] Risultato: {}", responseBody);
        logger.info("[CoverageController] [POST /score/opponent] OK 200");
        return ResponseEntity.status(HttpStatus.OK).header("Content-Type", "application/json").body(responseBody);
    }

    @Operation(
            summary = "Calculate EvoSuite coverage for a player",
            description = "Receives a JSON request with player coverage data and returns the calculated EvoSuite coverage metrics."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Coverage successfully calculated",
                    content = @Content(schema = @Schema(implementation = EvosuiteCoverageDTO.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "JSON payload containing the player coverage request data",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = StudentCoverageRequestDTO.class)
            )
    )
    @PostMapping(value = "/coverage/player", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> calculateStudentEvosuiteCoverage(
            @RequestBody StudentCoverageRequestDTO request)
            throws InterruptedException, ExecutionException {
        logger.info("[CoverageController] [POST /coverage/player] Ricevuta richiesta");

        Callable<String> compilationTimedTask = () -> coverageService.calculatePlayerCoverage(request);

        Future<String> future = compileExecutor.submitTask(compilationTimedTask); // Se la coda è piena, viene lanciata una RejectedExecutionException

        String score = future.get(); // può lanciare InterruptedException, TimeoutException o altre eccezioni generiche

        EvosuiteCoverageDTO responseBody = BuildResponse.buildExtendedDTO(score);

        logger.info("[CoverageController] [POST /coverage/player] Risultato: {}", responseBody);
        logger.info("[CoverageController] [POST /coverage/player] OK 200");
        return ResponseEntity.status(HttpStatus.OK).header("Content-Type", "application/json").body(responseBody);

    }


}
