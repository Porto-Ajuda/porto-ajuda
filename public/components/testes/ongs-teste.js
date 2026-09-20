const ongs = [

    // =========================================================
    // PRAIA GRANDE
    // =========================================================

    {
        id: 1,
        nome: "Bom Prato",
        tipo: "Serviço público",
        categoria: "Alimentação",

        descricao:
            "Serviço público voltado ao acesso da população a refeições de baixo custo.",

        sobre:
            "O Bom Prato é uma iniciativa de segurança alimentar que busca ampliar o acesso da população a refeições de baixo custo, contribuindo para a redução da insegurança alimentar e para a promoção da dignidade social.",

        publico:
            "População em geral, especialmente pessoas em situação de vulnerabilidade social.",

        servicos: [
            "Oferta de refeições",
            "Acesso à alimentação de baixo custo",
            "Apoio à segurança alimentar"
        ],

        endereco: "Praia Grande - SP",
        latitude: -24.024220,
        longitude: -46.479764,

        ods: [2, 3, 10],

        pix: null
    },


    {
        id: 2,
        nome: "CRAS",
        tipo: "PUBLICO",
        categoria: "Assistência Social",

        descricao:
            "Unidade pública de assistência social que oferece atendimento e orientação às famílias e indivíduos em situação de vulnerabilidade.",

        sobre:
            "O Centro de Referência de Assistência Social (CRAS) atua como uma das principais portas de entrada da população para a assistência social, oferecendo acolhimento, orientação e encaminhamentos para serviços e benefícios sociais.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade ou risco social.",

        servicos: [
            "Atendimento e orientação social",
            "Acolhimento de famílias",
            "Encaminhamento para serviços públicos",
            "Orientação sobre benefícios sociais"
        ],

        endereco: "Praia Grande - SP",
        latitude: -24.019597,
        longitude: -46.476472,

        ods: [1, 3, 10, 16],

        pix: null
    },


    // =========================================================
    // SÃO VICENTE
    // =========================================================

    {
        id: 3,
        nome: "CRAS São Vicente",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade de assistência social destinada ao atendimento e acompanhamento de famílias e indivíduos em situação de vulnerabilidade.",

        sobre:
            "O CRAS São Vicente integra a rede de assistência social do município e oferece atendimento, orientação e encaminhamento para serviços e benefícios destinados à população em situação de vulnerabilidade social.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Atendimento social",
            "Orientação às famílias",
            "Encaminhamento para serviços públicos",
            "Acompanhamento social"
        ],

        endereco: "Av. Marechal Deodoro, 169 - Vila Valença, São Vicente - SP",
        latitude: -23.964563,
        longitude: -46.380521,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 4,
        nome: "CRAS Vila Margarida",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade de assistência social que oferece acolhimento, orientação e encaminhamento à população em situação de vulnerabilidade.",

        sobre:
            "O CRAS Vila Margarida faz parte da rede de proteção social do município, oferecendo atendimento e orientação às famílias e indivíduos que necessitam de apoio e acesso a políticas públicas de assistência social.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Acolhimento social",
            "Orientação",
            "Encaminhamento para serviços",
            "Acompanhamento de famílias"
        ],

        endereco: "Av. Nações Unidas, 696 - Vila Margarida, São Vicente - SP",
        latitude: -23.967060,
        longitude: -46.404049,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 5,
        nome: "CRAS Jóquei Clube",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade pública de assistência social voltada ao atendimento de famílias e indivíduos em situação de vulnerabilidade.",

        sobre:
            "O CRAS Jóquei Clube integra a rede de assistência social e atua no acolhimento, orientação e encaminhamento da população para serviços e benefícios sociais.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Atendimento social",
            "Orientação",
            "Encaminhamento para serviços públicos",
            "Acompanhamento social"
        ],

        endereco: "Av. Senador Salgado Filho, 224 - Vila Jóquei Clube, São Vicente - SP",
        latitude: -23.949897,
        longitude: -46.397579,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 6,
        nome: "CRAS Tancredo Neves",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade da rede pública de assistência social destinada ao atendimento de famílias e indivíduos em situação de vulnerabilidade.",

        sobre:
            "O CRAS Tancredo Neves oferece atendimento e orientação social, contribuindo para o acesso da população a políticas públicas, benefícios e serviços da rede de proteção social.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Atendimento social",
            "Orientação",
            "Encaminhamentos",
            "Acompanhamento de famílias"
        ],

        endereco: "Rua Quirino Mário Biasóli, 138 - Tancredo Neves, São Vicente - SP",
        latitude: -23.939570,
        longitude: -46.418360,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 7,
        nome: "CRAS Parque das Bandeiras",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade pública de assistência social voltada ao acolhimento e orientação de famílias e indivíduos em situação de vulnerabilidade.",

        sobre:
            "O CRAS Parque das Bandeiras integra a rede de proteção social e busca facilitar o acesso da população aos serviços, benefícios e demais recursos disponíveis na assistência social.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Acolhimento",
            "Orientação social",
            "Encaminhamento para serviços",
            "Acompanhamento familiar"
        ],

        endereco: "Rua Simão Jah Jah, 351 - Parque das Bandeiras, São Vicente - SP",
        latitude: -23.985907,
        longitude: -46.486263,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 8,
        nome: "CRAS Humaitá",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade da rede de assistência social destinada ao atendimento e orientação da população em situação de vulnerabilidade.",

        sobre:
            "O CRAS Humaitá atua como ponto de atendimento da assistência social, oferecendo acolhimento, orientação e encaminhamento para serviços e benefícios da rede pública.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Atendimento social",
            "Orientação",
            "Encaminhamentos",
            "Acompanhamento social"
        ],

        endereco: "Rua Nivaldo Leite da Silva - Humaitá, São Vicente - SP",
        latitude: -23.950637,
        longitude: -46.465868,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 9,
        nome: "Centro POP",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Serviço especializado da assistência social voltado ao atendimento da população em situação de rua.",

        sobre:
            "O Centro POP integra a rede de proteção social especializada e oferece atendimento destinado à população em situação de rua, contribuindo para o acesso a direitos, serviços públicos e outras formas de apoio social.",

        publico:
            "Pessoas em situação de rua.",

        servicos: [
            "Atendimento social",
            "Acolhimento",
            "Orientação",
            "Encaminhamento para serviços da rede",
            "Apoio para acesso a direitos"
        ],

        endereco: "Av. Capitão-Mor Aguiar, 436 - Centro, São Vicente - SP",
        latitude: -23.967479,
        longitude: -46.391505,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 10,
        nome: "Fundo Social de Solidariedade de São Vicente",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Iniciativa de apoio social voltada à mobilização de recursos e atendimento de pessoas em situação de vulnerabilidade.",

        sobre:
            "O Fundo Social de Solidariedade atua na promoção de ações de apoio social e na mobilização de recursos destinados a pessoas e famílias que enfrentam situações de vulnerabilidade.",

        publico:
            "Pessoas e famílias em situação de vulnerabilidade social.",

        servicos: [
            "Ações de apoio social",
            "Campanhas de solidariedade",
            "Distribuição de doações",
            "Mobilização de recursos"
        ],

        endereco: "Rua Benedito Calixto, 205 - Gonzaguinha, São Vicente - SP",
        latitude: -23.967280,
        longitude: -46.376197,

        ods: [1, 2, 10, 17],

        pix: null
    },


    // =========================================================
    // SANTOS
    // =========================================================

    {
        id: 11,
        nome: "CRAS Centro",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade de assistência social que oferece acolhimento, orientação e encaminhamento para a população em situação de vulnerabilidade.",

        sobre:
            "O CRAS Centro integra a rede de proteção social e atua no atendimento de famílias e indivíduos, facilitando o acesso a serviços, benefícios e políticas públicas de assistência social.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Atendimento social",
            "Orientação",
            "Encaminhamentos",
            "Acompanhamento familiar"
        ],

        endereco: "Rua Sete de Setembro, 45 - Vila Nova, Santos - SP",
        latitude: -23.961586,
        longitude: -46.315634,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 12,
        nome: "CRAS Zona da Orla e Intermediária",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade da rede de assistência social destinada ao atendimento e orientação de famílias e indivíduos.",

        sobre:
            "O CRAS Zona da Orla e Intermediária integra a rede de assistência social do município, oferecendo atendimento e orientação para facilitar o acesso da população às políticas públicas e aos serviços da rede.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Atendimento social",
            "Orientação",
            "Encaminhamento para serviços",
            "Acompanhamento social"
        ],

        endereco: "Av. Affonso Penna, 185 - Macuco, Santos - SP",
        latitude: null,
        longitude: null,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 13,
        nome: "CRAS Bom Retiro",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade pública de assistência social voltada ao atendimento e acompanhamento de famílias em situação de vulnerabilidade.",

        sobre:
            "O CRAS Bom Retiro integra a rede de proteção social e oferece atendimento, orientação e encaminhamento para serviços e benefícios sociais.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Atendimento social",
            "Orientação",
            "Encaminhamentos",
            "Acompanhamento familiar"
        ],

        endereco: "Av. Nossa Senhora de Fátima, 517 - Caneleira, Santos - SP",
        latitude: -23.940977,
        longitude: -46.364981,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 14,
        nome: "CRAS Rádio Clube",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade da rede pública de assistência social que atende famílias e indivíduos em situação de vulnerabilidade.",

        sobre:
            "O CRAS Rádio Clube oferece atendimento e orientação social, contribuindo para o acesso da população aos serviços e benefícios da rede de proteção social.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Atendimento social",
            "Orientação",
            "Encaminhamentos",
            "Acompanhamento"
        ],

        endereco: "Av. Brigadeiro Faria Lima, 677 - Rádio Clube, Santos - SP",
        latitude: -23.939668,
        longitude: -46.388126,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 15,
        nome: "CRAS Chico de Paula",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade pública voltada ao atendimento e orientação de famílias e indivíduos em situação de vulnerabilidade social.",

        sobre:
            "O CRAS Chico de Paula faz parte da rede de assistência social e atua no acolhimento, orientação e encaminhamento da população para serviços e benefícios disponíveis.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Atendimento social",
            "Orientação",
            "Encaminhamento",
            "Acompanhamento familiar"
        ],

        endereco: "Av. Marginal da Via Anchieta, 218 - Chico de Paula, Santos - SP",
        latitude: -23.929887,
        longitude: -46.365697,

        ods: [1, 3, 10, 16],

        pix: null
    },


    {
        id: 16,
        nome: "CRAS Nova Cintra",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Unidade de assistência social destinada ao atendimento e orientação da população em situação de vulnerabilidade.",

        sobre:
            "O CRAS Nova Cintra integra a rede de proteção social e oferece atendimento, orientação e encaminhamento para serviços e benefícios sociais.",

        publico:
            "Famílias e indivíduos em situação de vulnerabilidade social.",

        servicos: [
            "Atendimento social",
            "Orientação",
            "Encaminhamentos",
            "Acompanhamento"
        ],

        endereco: "Av. Guilherme Russo, 77 - Morro Nova Cintra, Santos - SP",
        latitude: -23.942676,
        longitude: -46.346748,

        ods: [1, 3, 10, 16],

        pix: null
    },


    // =========================================================
    // PROTEÇÃO ANIMAL
    // =========================================================

    {
        id: 17,
        nome: "ONG Defesa da Vida Animal",
        tipo: "ONG",
        categoria: "Proteção Animal",

        descricao:
            "Organização voltada à proteção, cuidado e promoção do bem-estar dos animais.",

        sobre:
            "A iniciativa está relacionada à proteção animal e à promoção de melhores condições de cuidado e bem-estar para os animais, contribuindo também para a conscientização da comunidade sobre a responsabilidade e o respeito aos animais.",

        publico:
            "Animais em situação de vulnerabilidade e pessoas interessadas em proteção animal.",

        servicos: [
            "Proteção animal",
            "Ações de conscientização",
            "Promoção do bem-estar animal"
        ],

        endereco: "Rua Almirante Tamandaré, 136 - Macuco, Santos - SP",
        latitude: -23.961212,
        longitude: -46.309183,

        ods: [3, 11, 15],

        pix: null
    },


    {
        id: 18,
        nome: "Ong Patinhas que Brilham",
        tipo: "ONG",
        categoria: "Proteção Animal",

        descricao:
            "Organização dedicada à proteção e ao bem-estar dos animais.",

        sobre:
            "A iniciativa atua no campo da proteção animal, buscando contribuir para melhores condições de vida e para a conscientização da sociedade sobre a importância da guarda responsável e do respeito aos animais.",

        publico:
            "Animais em situação de vulnerabilidade e comunidade.",

        servicos: [
            "Proteção animal",
            "Ações de conscientização",
            "Promoção da guarda responsável"
        ],

        endereco: "Rua Carvalho de Mendonça, 670 - Marapé, Santos - SP",
        latitude: -23.955434,
        longitude: -46.345885,

        ods: [3, 11, 15],

        pix: null
    },


    {
        id: 19,
        nome: "Instituto Eliseu",
        tipo: "ONG",
        categoria: "Proteção Animal",

        descricao:
            "Organização social relacionada à proteção e ao cuidado com animais.",

        sobre:
            "O Instituto Eliseu está classificado no Porto Ajuda na área de proteção animal, reunindo ações voltadas ao cuidado e à promoção do bem-estar dos animais.",

        publico:
            "Animais em situação de vulnerabilidade e comunidade.",

        servicos: [
            "Proteção animal",
            "Ações sociais",
            "Promoção do bem-estar animal"
        ],

        endereco: "Rua São Paulo, 120 - Vila Belmiro, Santos - SP",
        latitude: -23.951428,
        longitude: -46.334620,

        ods: [3, 11, 15],

        pix: null
    },


    // =========================================================
    // MEIO AMBIENTE
    // =========================================================

    {
        id: 20,
        nome: "ONG Vidas Recicladas",
        tipo: "ONG",
        categoria: "Meio Ambiente",

        descricao:
            "Organização voltada a iniciativas relacionadas à sustentabilidade, reciclagem e conscientização ambiental.",

        sobre:
            "A iniciativa está relacionada à promoção da sustentabilidade e à conscientização ambiental, valorizando práticas que contribuem para a redução de resíduos e para uma relação mais responsável com o meio ambiente.",

        publico:
            "Comunidade em geral e pessoas interessadas em sustentabilidade.",

        servicos: [
            "Educação ambiental",
            "Conscientização sobre reciclagem",
            "Ações de sustentabilidade",
            "Mobilização comunitária"
        ],

        endereco: "Rua Campos Mello, 157 - Vila Mathias, Santos - SP",
        latitude: -23.947518,
        longitude: -46.320458,

        ods: [11, 12, 13, 15],

        pix: null
    },


    // =========================================================
    // FUNDO SOCIAL
    // =========================================================

    {
        id: 21,
        nome: "Fundo Social de Solidariedade de Santos",
        tipo: "Serviço público",
        categoria: "Assistência Social",

        descricao:
            "Serviço de apoio social voltado à promoção de ações de solidariedade e atendimento a pessoas em situação de vulnerabilidade.",

        sobre:
            "O Fundo Social de Solidariedade de Santos desenvolve e apoia ações de caráter social e solidário, contribuindo para a mobilização de recursos e para o atendimento de pessoas e famílias em situação de vulnerabilidade.",

        publico:
            "Pessoas e famílias em situação de vulnerabilidade social.",

        servicos: [
            "Ações de solidariedade",
            "Campanhas sociais",
            "Distribuição de doações",
            "Mobilização comunitária"
        ],

        endereco: "Av. Conselheiro Nébias, 388 - Paquetá, Santos - SP",
        latitude: -23.950810,
        longitude: -46.322802,

        ods: [1, 2, 10, 17],

        pix: null
    },


    // =========================================================
    // IDOSOS
    // =========================================================

    {
        id: 22,
        nome: "MÃOS QUE AJUDAM!",
        tipo: "Serviço público",
        categoria: "Idosos",

        descricao:
            "Iniciativa classificada na área de apoio social e atenção à população idosa.",

        sobre:
            "A iniciativa está cadastrada no Porto Ajuda na categoria de atenção à pessoa idosa, com foco no apoio social e na promoção de melhores condições de vida e integração da população atendida.",

        publico:
            "Pessoas idosas e comunidade.",

        servicos: [
            "Apoio social",
            "Ações voltadas à população idosa",
            "Integração comunitária"
        ],

        endereco: "Santos - SP",
        latitude: -23.973499,
        longitude: -46.318576,

        imagem: "../assets/maos-que-ajudam.png",

        ods: [3, 10, 11],

        pix: {
            chave: "tccportoajuda@gmail.com",
            nome: "PORTO AJUDA",
            cidade: "PRAIA GRANDE"
        }
    }

];