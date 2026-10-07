package com.somepro.interfaces.rest.rate;

import com.somepro.application.rate.PawnRateAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.common.vo.PageVO;
import com.somepro.interfaces.rest.rate.converter.PawnRateVoConverter;
import com.somepro.interfaces.rest.rate.dto.RateCreateRequest;
import com.somepro.interfaces.rest.rate.dto.RateIdRequest;
import com.somepro.interfaces.rest.rate.dto.RateUpdateRequest;
import com.somepro.interfaces.rest.rate.vo.PawnRateVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 费率配置模块用户接口层：录入、修改、查看、停用、按条件翻配置清单。
 *
 * 只做协议适配（参数解析、VO 转换、Result 包装），业务编排在 {@link PawnRateAppService}。
 * 入参统一走 @ModelAttribute / @RequestParam：表单 / query string / x-www-form-urlencoded 都能接，
 * 便于柜台端直接调用。
 */
@RestController
@RequestMapping("/api/rate")
public class PawnRateController {

    private final PawnRateAppService pawnRateAppService;

    public PawnRateController(PawnRateAppService pawnRateAppService) {
        this.pawnRateAppService = pawnRateAppService;
    }

    /**
     * 录入：类别只认当物五种，新配默认启用；同类别已有配置（不论启用停用）会被挡回。
     * 月利率/月综合费率/折当率上限都写成小数比例（如 0.005 / 0.7）。
     */
    @PostMapping("/create")
    public Mono<Result<PawnRateVO>> create(@ModelAttribute RateCreateRequest request) {
        return pawnRateAppService.create(request.getCategory(), request.getMonthlyRate(),
                        request.getServiceRate(), request.getMaxLoanRatio())
                .map(PawnRateVoConverter::toVo)
                .map(Result::ok);
    }

    /** 修改：费率三项全量重录；status 传 ENABLED/DISABLED 顺带切状态，不传不动。 */
    @PostMapping("/update")
    public Mono<Result<PawnRateVO>> update(@ModelAttribute RateUpdateRequest request) {
        return pawnRateAppService.update(request.getId(), request.getMonthlyRate(),
                        request.getServiceRate(), request.getMaxLoanRatio(), request.getStatus())
                .map(PawnRateVoConverter::toVo)
                .map(Result::ok);
    }

    /** 停用：该类别新开票随即卡住；已开出的票按票上快照照旧。 */
    @PostMapping("/disable")
    public Mono<Result<PawnRateVO>> disable(@ModelAttribute RateIdRequest request) {
        return pawnRateAppService.disable(request.getId())
                .map(PawnRateVoConverter::toVo)
                .map(Result::ok);
    }

    /** 查看：id 或 category 任一指定。 */
    @GetMapping("/detail")
    public Mono<Result<PawnRateVO>> detail(@RequestParam(required = false) Long id,
                                           @RequestParam(required = false) String category) {
        return pawnRateAppService.detail(id, category)
                .map(PawnRateVoConverter::toVo)
                .map(Result::ok);
    }

    /**
     * 翻配置清单：类别/状态随意拼，都不填翻整份。
     * pageNum/pageSize 由请求说了算，每行带类别便于按当物类别对号。
     */
    @GetMapping("/list")
    public Mono<Result<PageVO<PawnRateVO>>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String status) {
        return pawnRateAppService.page(pageNum, pageSize, category, status)
                .map(PawnRateVoConverter::toPageVo)
                .map(Result::ok);
    }
}
