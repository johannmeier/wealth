package de.wsc.wealth.service;

import de.wsc.wealth.domain.Bank;
import de.wsc.wealth.repository.BankRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class BankService {

    private final BankRepository bankRepository;

    public BankService(BankRepository bankRepository) {
        this.bankRepository = bankRepository;
    }

    public List<Bank> findAll() {
        return bankRepository.findAll().stream()
            .sorted(Comparator.comparing(Bank::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    public Optional<Bank> findById(Long id) {
        return bankRepository.findById(id);
    }

    public void save(Bank bank) {
        bankRepository.save(bank);
    }

    public void delete(Long id) {
        bankRepository.deleteById(id);
    }
}
