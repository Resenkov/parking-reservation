package resenkov.work.parkinguserservice.service;


import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resenkov.work.parkinguserservice.entity.Account;
import resenkov.work.parkinguserservice.repository.AccountRepository;

@Service
@Transactional
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public Account findById(Long id) {
        return repository.findById(id).orElseThrow(
                ()->new EntityNotFoundException("Account not found"));
    }
}
