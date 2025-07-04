package resenkov.work.parkinguserservice.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import resenkov.work.parkinguserservice.entity.User;
import resenkov.work.parkinguserservice.service.UserService;

@RestController
@Log4j2
@RequestMapping("/user")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/email")
    public ResponseEntity<User> findByEmail(String email){
        if (!email.isEmpty()){
            log.info("email found");
            return ResponseEntity.ok(service.findByEmail(email));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/add")
    public ResponseEntity<User> addUser(@RequestBody User user){
        if(!user.getEmail().isEmpty()){
            return ResponseEntity.ok(service.addUser(user));
        }
        return new ResponseEntity<> (HttpStatus.NOT_ACCEPTABLE);
    }

    @PutMapping("/update")
    public ResponseEntity<User> updateUser(@RequestBody User user){
        if(!user.getEmail().isEmpty()){
            return ResponseEntity.ok(service.updateUser(user));
        }
        return new ResponseEntity<> (HttpStatus.NOT_ACCEPTABLE);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity deleteUser(@PathVariable Long id){
        service.deleteUserById(id);
        return ResponseEntity.ok().build();
    }
}
