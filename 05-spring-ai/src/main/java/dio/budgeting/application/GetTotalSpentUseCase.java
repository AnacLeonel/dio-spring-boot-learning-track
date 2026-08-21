package dio.budgeting.application;

import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class GetTotalSpentUseCase {
    private final TransactionRepository transactionRepository;

    public GetTotalSpentUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "get-total-spent", description = "Calcula o valor total gasto somando todas as transações registradas")
    public long execute() {
        return transactionRepository.findAll()
                .stream()
                .mapToLong(transaction -> transaction.getAmount())
                .sum();
    }
}
