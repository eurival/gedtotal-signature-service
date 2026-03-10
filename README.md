# gedtotal-signature-service

## Responsabilidade

Microserviço responsável por:

- consumir `custodia.signature.request`
- buscar payload e binário do documento no `gedtotalapi`
- executar assinatura avançada do sistema
- executar assinatura qualificada ICP-Brasil quando configurada
- executar carimbo do tempo RFC3161 quando configurado
- devolver o artefato assinado ao `gedtotalapi`
- publicar `custodia.signature.result` ou `custodia.signature.failure`

## Papel na arquitetura

Este serviço:

- não acessa o banco do `gedtotalapi`
- não decide o backend físico do artefato
- não usa credenciais de S3 do projeto
- não deve sobrescrever o documento original

O storage final do artefato pertence ao `gedtotalapi`, que deve respeitar `Projeto.tipoHospedage`.

## Fluxo

1. consome comando Kafka em `custodia.signature.request`
2. chama `GET /api/internal/custodia/documentos/{arquivoId}/payload`
3. chama `GET /api/internal/custodia/documentos/{arquivoId}/content`
4. processa as etapas solicitadas
5. envia o PDF gerado para `POST /api/internal/custodia/documentos/{arquivoId}/artifacts`
6. publica resultado em `custodia.signature.result`
7. em caso de erro, publica `custodia.signature.failure`

## Configuração mínima

Principais propriedades em `src/main/resources/application.yaml`:

- `spring.kafka.bootstrap-servers`
- `app.internal-api.gedtotalapi-base-url`
- `app.internal-api.bearer-token`
- `app.kafka.topics.signature-request`
- `app.kafka.topics.signature-result`
- `app.kafka.topics.signature-failure`

### Variáveis úteis para teste local

```bash
export KAFKA_BOOTSTRAP_SERVERS=15.229.173.87:19092
export GEDTOTALAPI_BEARER_TOKEN='SEU_TOKEN_AQUI'
```

Observação:

- `GEDTOTALAPI_BEARER_TOKEN` é solução transitória de teste local
- a solução final deve ser autenticação service-to-service

## Portas

- HTTP: `8091`

## Tópicos Kafka

Consome:

- `custodia.signature.request`

Publica:

- `custodia.signature.result`
- `custodia.signature.failure`

## Etapas suportadas

- `ASSINATURA_AVANCADA_SISTEMA`
- `ASSINATURA_QUALIFICADA_ICP_BRASIL`
- `CARIMBO_TEMPO`

## Render visual suportado

O worker agora recebe do `gedtotalapi`:

- `codigoValidacao`
- `urlValidacao`
- politica visual do `Formulario`

Com isso, o PDF pode ser renderizado em:

- `ULTIMA_PAGINA_HORIZONTAL`
- `ULTIMA_PAGINA_VERTICAL`
- `TODAS_AS_PAGINAS_VERTICAL`
- `TODAS_AS_PAGINAS_HORIZONTAL`
- `PAGINA_CERTIFICADO`
- `ULTIMA_PAGINA_E_CERTIFICADO`

Recursos visuais atualmente suportados:

- QR code
- codigo de validacao
- hash do documento
- metadados PDF basicos
- template visual GedTotal / ICP-Brasil

Limitacoes atuais:

- a URL de validacao ainda depende do valor vindo do `gedtotalapi`
- fluxo automatico de participantes cobre hoje apenas o operador autenticado

## Execução local

```bash
./mvnw spring-boot:run
```

## Teste local mínimo

Pré-requisitos:

- Kafka acessível
- `gedtotalapi` rodando
- token válido para os endpoints internos do `gedtotalapi`

Configuração recomendada para primeiro teste:

- `qualified-icp-brasil.enabled=false`
- `timestamp.enabled=false`

Disparo pela API:

```bash
curl -X POST "http://localhost:8080/api/custodia/documentos/ARQUIVO_ID/signature-jobs" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{
    "etapas": ["ASSINATURA_AVANCADA_SISTEMA"],
    "traceId": "teste-custodia-001"
  }'
```

## ICP-Brasil

Para habilitar assinatura qualificada:

- `app.signature.qualified-icp-brasil.enabled=true`
- configurar:
  - `key-store-path`
  - `key-store-password`
  - `key-alias`
  - `key-password`

## TSA

Para habilitar timestamp RFC3161:

- `app.signature.timestamp.enabled=true`
- configurar:
  - `tsa-url`
  - `authority-name`

## Observações operacionais

- a primeira execução do PDFBox pode montar cache de fontes e demorar mais
- o resultado da assinatura só deve ser considerado válido quando o `gedtotalapi` consumir `custodia.signature.result`
- o artefato assinado deve aparecer no `gedtotalapi` como novo `Arquivo` derivado
