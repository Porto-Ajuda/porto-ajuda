package com.portoajuda.aplicacao_osc.controller;

import com.portoajuda.aplicacao_osc.entity.Usuario;
import com.portoajuda.aplicacao_osc.service.DonationCampaignService;
import com.portoajuda.aplicacao_osc.service.DonationCampaignService.CampaignInput;
import com.portoajuda.aplicacao_osc.dto.request.CreateCampaignQuoteDTO;
import com.portoajuda.aplicacao_osc.dto.response.CampaignQuoteDTO;
import com.portoajuda.aplicacao_osc.dto.response.CampaignCheckoutDTO;
import com.portoajuda.aplicacao_osc.service.CampaignDonationPaymentService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/donation-campaigns")
@RequiredArgsConstructor
public class DonationCampaignController {
    private static final Logger log=LoggerFactory.getLogger(DonationCampaignController.class);
    private final DonationCampaignService campaigns;
    private final CampaignDonationPaymentService payments;
    @GetMapping public List<Map<String,Object>> list(){ return campaigns.listPublic(); }
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable long id){ return campaigns.detail(id); }
    @GetMapping("/{id}/payment-availability") public Map<String,Object> paymentAvailability(@PathVariable long id){ return payments.availability(id); }
    @PreAuthorize("hasRole('USUARIO')")
    @PostMapping("/{id}/quotes")
    public CampaignQuoteDTO quote(@PathVariable long id,@RequestBody CreateCampaignQuoteDTO request,
            @AuthenticationPrincipal Usuario donor){ return payments.quote(id,donor,request.netAmountCents(),request.paymentMethod()); }
    @PreAuthorize("hasRole('USUARIO')")
    @PostMapping("/quotes/{quoteId}/checkout")
    public CampaignCheckoutDTO checkout(@PathVariable String quoteId,@RequestBody ConfirmQuote request,
            @AuthenticationPrincipal Usuario donor){ return payments.checkout(quoteId,donor,request!=null&&request.confirmed()); }
    @PreAuthorize("hasRole('USUARIO')")
    @GetMapping("/payments/session/{sessionId}")
    public com.portoajuda.aplicacao_osc.dto.response.CampaignPaymentStatusDTO status(@PathVariable String sessionId,
            @AuthenticationPrincipal Usuario donor){ return payments.status(sessionId,donor); }
    @PreAuthorize("hasRole('USUARIO')")
    @GetMapping("/history")
    public List<Map<String,Object>> history(@AuthenticationPrincipal Usuario donor){ return payments.donorHistory(donor); }
    @PreAuthorize("hasRole('OSC')")
    @GetMapping("/organizations/{oscId}/manage")
    public List<Map<String,Object>> manage(@PathVariable Integer oscId,@AuthenticationPrincipal Usuario actor){ return campaigns.manage(actor,oscId); }
    @PreAuthorize("hasRole('OSC')")
    @PostMapping(value="/organizations/{oscId}/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> uploadImage(@PathVariable Integer oscId,@RequestPart("image") MultipartFile image,
            @AuthenticationPrincipal Usuario actor){ return campaigns.uploadImage(actor,oscId,image); }
    @PreAuthorize("hasRole('OSC')")
    @PostMapping("/organizations/{oscId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@PathVariable Integer oscId,@RequestBody CampaignInput input,@AuthenticationPrincipal Usuario actor){
        return campaigns.save(actor,oscId,null,input);
    }
    @PreAuthorize("hasRole('OSC')")
    @PostMapping(value="/organizations/{oscId}/with-image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createWithImage(@PathVariable Integer oscId,@RequestPart("dados") CampaignInput input,
            @RequestPart(value="imagem",required=false) MultipartFile image,@AuthenticationPrincipal Usuario actor){
        return campaigns.save(actor,oscId,null,input,image);
    }
    @PreAuthorize("hasRole('OSC')")
    @PutMapping("/{id}/organizations/{oscId}")
    public Map<String,Object> update(@PathVariable long id,@PathVariable Integer oscId,@RequestBody CampaignInput input,@AuthenticationPrincipal Usuario actor){
        return campaigns.save(actor,oscId,id,input);
    }
    @PreAuthorize("hasRole('OSC')")
    @PutMapping(value="/{id}/organizations/{oscId}/with-image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> updateWithImage(@PathVariable long id,@PathVariable Integer oscId,@RequestPart("dados") CampaignInput input,
            @RequestPart(value="imagem",required=false) MultipartFile image,@AuthenticationPrincipal Usuario actor){
        return campaigns.save(actor,oscId,id,input,image);
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,String>> databaseFailure(DataAccessException failure){
        log.error("Campaign database operation failed",failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message","Não foi possível salvar a vaquinha agora. Seus dados foram mantidos; tente novamente."));
    }
    public record ConfirmQuote(boolean confirmed){}

}
