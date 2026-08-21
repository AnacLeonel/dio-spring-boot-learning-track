package dio.budgeting.infrastructure.http;

import dio.budgeting.application.GetTotalSpentUseCase;
import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;
    private final GetTotalSpentUseCase getTotalSpentUseCase;

    private final ChatClient chatClient;

    // TranscriptionModel e TextToSpeechModel removidos temporariamente.
    // Dependiam da OpenAI (Whisper e TTS), que exige créditos pagos.
    // O fluxo de voz completo está documentado no endpoint /ai (comentado abaixo).
    // Nesta versão local (Ollama), o comando é recebido como texto via /ai/text.

    public TransactionController(PersistTransactionUseCase persistTransactionUseCase,
                                 ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
                                 GetTotalSpentUseCase getTotalSpentUseCase,
                                 @Value("classpath:prompts/system-message.st") Resource systemPrompt,
                                 ChatClient.Builder chatClientBuilder) throws IOException {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.getTotalSpentUseCase = getTotalSpentUseCase;
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(Charset.defaultCharset()))
                .defaultTools(persistTransactionUseCase, listTransactionsByCategoryUseCase, getTotalSpentUseCase)
                .build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@RequestBody TransactionRequest request) {
        var transaction = persistTransactionUseCase.execute(request.toInput());
        return TransactionResponse.from(transaction);
    }

    @GetMapping("/{category}")
    public List<TransactionResponse> readTransactions(@PathVariable Category category) {
        return listTransactionsByCategoryUseCase.execute(category).stream().map(TransactionResponse::from).toList();
    }

    // Endpoint adaptado: recebe texto direto (sem transcrição de áudio),
    // usa o ChatClient (Ollama local) para interpretar o comando e chamar as tools,
    // e retorna a resposta em texto (sem conversão para áudio).
    @PostMapping("/ai/text")
    public Map<String, String> processTextCommand(@RequestBody Map<String, String> body) {
        var userMessage = body.get("message");
        var result = chatClient.prompt().user(userMessage).call().content();
        return Map.of("response", result);
    }

    /*
    // Endpoint original, funcional com créditos da OpenAI configurados:
    // recebe áudio, transcreve com Whisper, processa com o ChatClient,
    // converte a resposta em áudio MP3 com o TextToSpeechModel.
    //
    // Requer no application.properties:
    // spring.ai.model.audio.transcription=openai
    // spring.ai.model.audio.speech=openai
    // e os beans TranscriptionModel / TextToSpeechModel injetados no construtor.
    //
    // @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "audio/mp3")
    // ResponseEntity<Resource> transcribe(@RequestParam("file") MultipartFile file) {
    //     var userMessage = transcriptionModel.transcribe(file.getResource());
    //     var result = chatClient.prompt().user(userMessage).call().content();
    //     byte[] audio = textToSpeechModel.call(result);
    //     var resource = new ByteArrayResource(audio);
    //     return ResponseEntity.ok()
    //             .header(HttpHeaders.CONTENT_DISPOSITION,
    //                     ContentDisposition.attachment().filename("audio.mp3").build().toString())
    //             .body(resource);
    // }
    */
}