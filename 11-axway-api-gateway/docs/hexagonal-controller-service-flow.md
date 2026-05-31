# Como Controller e Service se Comunicam na Arquitetura Hexagonal

> Exemplo prático: `UserController` ↔ `UserProfileService` no `user-service`.

---

## A pergunta

> O `UserController` se comunica com o `UserProfileService`?

**Sim — mas indiretamente, através das portas (interfaces).** Essa indireção é o coração da **inversão de dependência** da arquitetura hexagonal.

---

## O que o Controller realmente conhece

Trecho do `UserController`:

```java
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final FindUserProfileUseCase findUserProfileUseCase;     // INTERFACE
    private final ManageUserProfileUseCase manageUserProfileUseCase; // INTERFACE
    private final JwtService jwtService;
    // ...
}
```

O controller depende de **interfaces** definidas em `domain/port/in/`. **Não** importa `UserProfileService` em lugar nenhum do código.

---

## Quem é a implementação injetada em runtime

`UserProfileService` (em `application/service/`) implementa as duas portas in:

```java
@Service
public class UserProfileService implements
        FindUserProfileUseCase,        // ← implementa porta in
        ManageUserProfileUseCase {     // ← implementa porta in
    // ...
}
```

Como ele é o único `@Service` que implementa esses contratos, o Spring resolve a injeção automaticamente:

```
UserController (precisa de FindUserProfileUseCase)
       ↓ Spring olha "quem implementa essa interface?"
       ↓ encontra UserProfileService
       ↓ injeta a instância
UserController.findUserProfileUseCase = UserProfileService instance
```

---

## Diagrama de dependências

```
┌────────────────────────────────────────────────┐
│ infrastructure/adapter/in/web                  │
│   UserController                               │
│   └── depende de ──┐                           │
└─────────────────── │ ──────────────────────────┘
                    ↓
┌─────────────────── │ ──────────────────────────┐
│ domain/port/in     ↓                           │
│   FindUserProfileUseCase  (interface)          │
│   ManageUserProfileUseCase  (interface)        │
└─────────────────── ↑ ──────────────────────────┘
                    │ implementa
┌─────────────────── │ ──────────────────────────┐
│ application/service│                           │
│   UserProfileService  (impl)                   │
│   └── depende de ──┐                           │
└─────────────────── │ ──────────────────────────┘
                    ↓
┌─────────────────── │ ──────────────────────────┐
│ domain/port/out    ↓                           │
│   UserProfileRepository  (interface)           │
│   UserEventPublisher  (interface)              │
└─────────────────── ↑ ──────────────────────────┘
                    │ implementa
┌─────────────────── │ ──────────────────────────┐
│ infrastructure/adapter/out/                    │
│   UserProfilePersistenceAdapter                │
│   OutboxUserEventPublisher                     │
└────────────────────────────────────────────────┘
```

A **direção das setas** é a regra: `infrastructure` aponta pra `domain`, nunca o contrário. O domain define os contratos, todo o resto se adapta a eles.

---

## Por que isso importa na prática

### 1. Testabilidade
Em um teste do `UserController`, dá pra mockar `FindUserProfileUseCase` direto, sem instanciar `UserProfileService` nem o JPA.

```java
@Test
void getMyProfile_returnsProfile() {
    var useCase = mock(FindUserProfileUseCase.class);
    when(useCase.findByUserId(42L)).thenReturn(somePofile);
    // não precisa de Spring, JPA, banco — testa o controller isolado
}
```

### 2. Substituibilidade
Amanhã, se quiséssemos partir o `UserProfileService` em dois — `UserProfileQueryService` (lê) e `UserProfileCommandService` (escreve), aplicando CQRS — o **controller não muda uma linha**. Cada novo service implementa uma das portas in, e o Spring resolve a injeção.

### 3. Domínio puro
O `UserProfileService` não conhece HTTP, JSON, Spring MVC ou códigos de status. Recebe um `Command` (POJO de domínio) e devolve um `UserProfile` (POJO de domínio). Quem traduz HTTP ↔ domínio é o controller.

```java
// O service recebe e devolve tipos de domínio
public UserProfile create(CreateProfileCommand command) { ... }

// O controller traduz HTTP <-> domínio
@PostMapping("/me")
public ResponseEntity<UserProfileResponse> createProfile(
        @RequestHeader("Authorization") String authHeader,
        @Valid @RequestBody UserProfileRequest request
) {
    Long userId = extractUserId(authHeader);
    var command = new CreateProfileCommand(userId, request.name(), request.email(), request.phone());
    var profile = manageUserProfileUseCase.create(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(UserProfileResponse.from(profile));
}
```

---

## Fluxo completo de uma requisição

Quando chega `POST /users/me`:

```
1. Spring MVC roteia → UserController.createProfile()
2. UserController.extractUserId(header) → JWT → Long userId
3. UserController cria CreateProfileCommand (domain DTO)
4. UserController chama manageUserProfileUseCase.create(command)
   ↓ (Spring resolveu pra UserProfileService no startup)
5. UserProfileService.create():
   - userProfileRepository.existsByUserId()       (porta out)
   - UserProfile.newProfile(...)                  (factory de domínio)
   - userProfileRepository.save()                 (porta out)
   - userEventPublisher.publishProfileCreated()   (porta out)
6. Retorna UserProfile (domain)
7. UserController converte com UserProfileResponse.from(profile)
8. Spring MVC serializa para JSON e responde 201
```

Cada `↓` é uma fronteira de camada. O segredo da hexagonal é manter essas fronteiras **unidirecionais**.

---

## Mapa rápido de pacotes (referência)

```
com.ecommerce.user/
├── domain/                          ← núcleo: regras + contratos
│   ├── model/                       (POJOs: UserProfile, Address, eventos)
│   ├── exception/
│   └── port/
│       ├── in/                      (interfaces de use cases)
│       └── out/                     (interfaces de repos/publishers)
├── application/                     ← orquestração dos use cases
│   └── service/                     (impl das portas in)
└── infrastructure/                  ← adaptação ao mundo externo
    ├── adapter/
    │   ├── in/                      (REST, RabbitListener)
    │   └── out/                     (JPA, RabbitTemplate, SMTP)
    └── config/                      (Spring Security, RabbitMQ, etc.)
```

**Regra de ouro**: `domain` não importa nada de `application` nem de `infrastructure`. `application` importa só de `domain`. `infrastructure` pode importar dos dois.

---

## Resumo em 3 frases

1. O Controller **não conhece** o Service concreto — só conhece a **interface** (porta in).
2. O Spring resolve a implementação em runtime e injeta automaticamente.
3. Isso permite trocar a implementação sem tocar no Controller, testar cada camada isoladamente, e manter o domínio puro de detalhes técnicos.
