package ai.finsight.forecast;
import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/forecast") public class ForecastController{private final ForecastService service;public ForecastController(ForecastService s){service=s;}@GetMapping public ForecastService.Forecast forecast(Authentication a){return service.forecast(a.getName());}}
