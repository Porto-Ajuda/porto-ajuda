# Capacidades do atendimento Sophie

## Disponíveis nesta versão

- Gemini interpreta o turno atual e o histórico recente da conversa selecionada, produz no máximo três pedidos de capacidade fechada e depois redige a resposta com os resultados devolvidos pelo servidor. A consulta usa o turno atual como filtro; referências a opções anteriores são resolvidas pelo histórico e correções não reaplicam filtros anteriores.
- Pesquisa pública limitada a ONGs, publicações e oportunidades de voluntariado. A visibilidade é aplicada pelos serviços existentes; causas secundárias de ONG também são consideradas. Localidade é filtro opcional vindo da mensagem da pessoa; rua, CEP e coordenadas são removidos antes de compor o contexto do provedor.
- Contagem de resultados públicos usa consultas paginadas limitadas. Quando o limite de varredura é atingido, a resposta deve ser apresentada como “pelo menos”, não como total exato. “Quantas delas?” conta os itens da resposta anterior; “quantas existem?” executa nova contagem.
- Comparação e detalhes usam causas, descrições, modalidades, atividades e localização que constam no conteúdo público consultado. Sophie explica a correspondência observável, sem endossar confiança, auditoria, impacto ou segurança financeira.
- Orientações mantidas pelo servidor cobrem acesso e recuperação de conta, cadastro de ONG, documentos de representação, publicações, candidaturas de voluntariado, acessibilidade, atendimento humano e o limite atual de doações. Os links de navegação são escolhidos por lista fechada e validados pelo backend.
- Uma busca de catálogo com causa pré-selecionada pode ser aberta como filtro editável. Os itens públicos têm links descritivos nos próprios nomes; nenhum caminho enviado pelo modelo é renderizado.
- Pergunta ambígua pode receber uma clarificação curta. Busca vazia, falha de busca, limite de privacidade e pedido de atendimento são estados distintos. O atendimento só entra na fila após solicitação direta; quando um caso exige equipe, Sophie oferece a ação de encaminhamento, sem prometer resposta imediata.

O planejador e o redator usam o mesmo bean `GeminiRequestBudget` compartilhado com análise documental; não existe orçamento de provedor separado. No modo local ativo, uma resposta com pesquisa pode usar duas chamadas (interpretação e redação). Flags padrão do repositório continuam desligadas. Nenhum histórico fora da conversa selecionada, dado de perfil ou documento privado é carregado como ferramenta. Conteúdo recuperado é dado não confiável, nunca instrução.

A seção Doações tem um destino de catálogo, mas Sophie não cria campanhas, confirma campanhas/destinos financeiros nem inicia pagamentos. Ela pode explicar esse limite; não presume que uma listagem seja um destino seguro.
## Ações que exigiriam uma ferramenta futura, autorização e confirmação próprias

- Consultar dados privados da conta, candidaturas individuais ou histórico pessoal.
- Criar, editar ou excluir conteúdo, oportunidades, perfil ou associação a uma ONG.
- Enviar candidatura, aprovar/rejeitar candidatos ou administrar membros.
- Registrar denúncias, decidir moderação, ajustar pontos ou alterar restrições.
- Enviar documentos de representação, acompanhar dados privados do processo ou tomar uma decisão de verificação.
- Alterar senha, e-mail, credenciais, sessões ou configurações protegidas.
- Criar campanhas, receber doações, iniciar pagamentos, reembolsos ou qualquer ação financeira.
- Enviar mensagens, e-mails, SMS ou anexos fora do fluxo normal e consentido da aplicação.

Essas ações permanecem nos fluxos existentes com autenticação, autorização do servidor, proteção CSRF e confirmação apropriada. Nenhuma é concedida por um pedido em linguagem natural. A Sophie nunca deve pedir senha, token de sessão ou código MFA.
