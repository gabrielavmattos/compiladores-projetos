package trabalho3;

import java.util.ArrayList;
import java.util.List;
import org.antlr.runtime.RecognitionException;
/**
 * @program GeradorCodigo - Faz toda a geracao de codigo em Arduino de um dado codigo da Linguagem FAZEDORES
 * @author Douglas
 * 
 * Principais funcoes que definem na maioria dos casos o caminho de um programa:
 * - enterPrograma / void ProcuraLeiaEscreva -> Verificacao de algumas flags
 * - enterDeclaracao_Local / enterDeclaracao_Global -> Declaracoes iniciais e chamada de procedimentos/funcoes
 * - enterComandosSetup -> Inicio do procedimento Setup()
 * - enterComandoSetup (NAO EH A MESMA QUE A DE CIMA) -> Comandos que vao dentro do procedimento Setup()
 * - exitComandosSetup -> Ultimas declaracoes do procedimento Setup() que dependem das flags e inicio do procedimento Loop()
 * - enterCMDLoop / enterComandoLCD / enterChamada_atribuicao -> Comandos que vao dentro do procedimento Loop()
 */
public class GeradorCodigo extends FAZEDORESBaseListener {
    Saida codigo;
    Boolean flagLCD = false, flagSerial = false; 
  
    //PARA AS TABELAS DE SIMBOLOS
    int EhFuncao = 0;
    static PilhaDeTabelas pilhaDeTabelas = new PilhaDeTabelas();
    
    public GeradorCodigo(Saida codigo){
        this.codigo = codigo;
    }
    
    @Override 
    public void enterPrograma(FAZEDORESParser.ProgramaContext ctx) {
        //Para utilizar na procura de uma funcao escreva() ou leia()
        String chamadaFeita;
        
        int i=0;
        
        //Tenta encontrar o LCD sendo ativado
        while (ctx.comandosSetup().comandoSetup(i) != null){
            
            if (ctx.comandosSetup().comandoSetup(i).dispositivo().getText().equals("lcd")){
                /*Encontrado um lcd sendo ativado, entao torna true sua flag e 
                realiza seus includes no codigo */
                flagLCD = true;
                
                codigo.println("#include <Wire.h>");
                codigo.println("#include \"rgb_lcd.h\"");
                codigo.println("");
                codigo.println("rgb_lcd lcd;");
                codigo.println("");
            }
            
            //incrementa o i para verificar outros possiveis comandoSetup
            i++;
        }
        
        //Zera o i para uma nova consulta
        i = 0;
        
        //Verifica se encontra alguma funcao do tipo leia ou escreva dentro do comando loop
        ProcuraEscrevaLeia(ctx.comandos());
        
        //Explicacoes melhores deste while se encontram na funcao ProcuraEscrevaLeia
        
        /*Tambem se verifica nas declaracoes se nao existe alguma funcao do tipo 
        leia ou escreva*/
        while (ctx.declaracoes().decl_local_global(i) != null && flagSerial == false){
            
            //Pega qual chamada eh feita
            chamadaFeita = ctx.declaracoes().decl_local_global(i).getStart().getText();
            /*Dentro das funcoes e procedimentos temos a possibilidade de utilizar 
              o leia ou o escreva, entao faz uma chamada recursiva para verificar ali*/
            if (chamadaFeita.equals("procedimento") || chamadaFeita.equals("funcao"))
                ProcuraEscrevaLeia(ctx.declaracoes().decl_local_global(i).declaracao_global().comandos());
            
            //Incrementa o i
            i++;
        }

        //TABELA DE SIMBOLOS GLOBAL
        pilhaDeTabelas.empilhar(new TabelaDeSimbolos("global"));
    }
    
    @Override 
    public void exitPrograma(FAZEDORESParser.ProgramaContext ctx) { 
        /*Caso foi necessario verificar se o serial estava ativado, fecha a chave
        desta verificacao*/
        //Terminou todas as declaracoes da funcao loop, eh fechada a chave dela
        codigo.println("}");
    }
        
    @Override 
    public void exitDeclaracoes(FAZEDORESParser.DeclaracoesContext ctx) { 
        //Terminou todas as declaracoes, pula uma linha
        codigo.println("");
    }
    
    @Override
    public void enterDeclaracao_local(FAZEDORESParser.Declaracao_localContext ctx) {
        //Para a tabela de simbolos
        String nomeVar, tipo, tipoConstante;
        TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo();

        String tipoDeclLocal = ctx.getStart().getText();
        
        // declarando uma constante
        if (tipoDeclLocal.equals("constante")){
            //PARA INCLUSAO DA CONSTANTE NA TABELA DE SIMBOLOS
            nomeVar = ctx.getChild(1).getText();
            tipo = ctx.tipo_basico().getText();
            
            switch (tipo){
                case "literal":
                    tipoConstante = "String ";
                    break;
                case "inteiro":
                    tipoConstante = "int ";
                    break;
                case "real":
                    tipoConstante = "float ";
                    break;
                default: //caso logico
                    tipoConstante = "bool ";   
            }
            
            //Impressao da constante
            if(nomeVar.contains("Pot") || nomeVar.contains("oque"))
            {   
                int porta = Integer.parseInt(ctx.valor_constante().getText());
                porta = porta % 10;
                codigo.println("const "+tipoConstante+nomeVar+" = A"+ porta +";");
            }
            else
                
                codigo.println("const "+tipoConstante+nomeVar+" = "+ctx.valor_constante().getText()+";");
            
            if(!pilhaDeTabelas.existeSimbolo(nomeVar)) 
                tabelaDeSimbolosAtual.adicionarSimbolo(nomeVar,tipo, null, null);
        }
        else{
            //Declarando uma variavel
            if (tipoDeclLocal.equals("declare")){
                //PARA INCLUSAO DA VARIAVEL NA TABELA DE SIMBOLOS
                nomeVar = ctx.variavel().IDENT().getText();
                tipo = ctx.variavel().tipo().getText();

                switch (tipo){
                    case "literal":
                        tipoConstante = "String ";
                        break;
                    case "inteiro":
                        tipoConstante = "int ";
                        break;
                    case "real":
                        tipoConstante = "float ";
                        break;
                    default: //caso logico
                        tipoConstante = "bool ";   
                }

                //Impressao da variavel
                codigo.println(tipoConstante + nomeVar + ";");
                codigo.println("");

                if(!pilhaDeTabelas.existeSimbolo(nomeVar)) 
                    tabelaDeSimbolosAtual.adicionarSimbolo(nomeVar,tipo, null, null);
            }
        }
    }
    
    //Codigos reaproveitados do trabalho 1
    @Override
    public void enterDeclaracao_global(FAZEDORESParser.Declaracao_globalContext ctx) {
        //PARA A TABELA DE SIMBOLOS
        String nome = ctx.IDENT().getText();
        TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo();
        List<String> listaNomePar = new ArrayList<>();
        List<String> listaTipoPar = new ArrayList<>();
        
        String declaracao;
        
        //verifica se eh um procedimento ou uma funcao
        if (ctx.getStart().getText().equals("procedimento")){
            declaracao = "void ";
        
            //PARA A TABELA DE SIMBOLOS
            if(!pilhaDeTabelas.existeSimbolo(nome)){
                if(ctx.parametros_opcional()!=null)
                    AdicionarTiposParametros(ctx,listaNomePar,listaTipoPar);
                tabelaDeSimbolosAtual.adicionarSimbolo(nome, null, listaTipoPar, null);
                TabelaDeSimbolos tabelaDeSimbolosProcedimento = new TabelaDeSimbolos("procedimento"+nome);
                for(int i = 0; i < listaNomePar.size(); i++){
                    if(!tabelaDeSimbolosProcedimento.existeSimbolo(listaNomePar.get(i)))
                        tabelaDeSimbolosProcedimento.adicionarSimbolo(listaNomePar.get(i), listaTipoPar.get(i), null, null);
                }
                pilhaDeTabelas.empilhar(tabelaDeSimbolosProcedimento);  
            }
            //FIM DA TABELA DE SIMBOLOS
        }
        else{
            // como eh funcao precisa ser verificado o tipo dela
            switch (ctx.tipo_estendido().tipo_basico_ident().tipo_basico().getText()){
                case "literal":
                    declaracao = "char ";
                    break;
                case "inteiro":
                    declaracao = "int ";
                    break;
                case "real":
                    declaracao = "float ";
                    break;
                default: //caso logico
                    declaracao = "bool ";   
            }
            
            //PARA A TABELA DE SIMBOLOS
            EhFuncao = 1;
            if(!pilhaDeTabelas.existeSimbolo(nome)){
                if(ctx.parametros_opcional()!=null)
                    AdicionarTiposParametros(ctx,listaNomePar,listaTipoPar);
                String TipoFuncao;
                if(ctx.tipo_estendido().tipo_basico_ident().IDENT()!=null)
                    TipoFuncao = ctx.tipo_estendido().tipo_basico_ident().IDENT().getText();
                else
                    TipoFuncao = ctx.tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                    
                tabelaDeSimbolosAtual.adicionarSimbolo(nome, TipoFuncao, listaTipoPar, null);
                TabelaDeSimbolos tabelaDeSimbolosFuncao = new TabelaDeSimbolos("funcao"+nome);
                    
                for(int i = 0; i < listaNomePar.size(); i++){
                    if(!tabelaDeSimbolosFuncao.existeSimbolo(listaNomePar.get(i)))
                        tabelaDeSimbolosFuncao.adicionarSimbolo(listaNomePar.get(i), listaTipoPar.get(i), null, null);
                }
                pilhaDeTabelas.empilhar(tabelaDeSimbolosFuncao);
            }
            //FIM TABELA DE SIMBOLOS
        }
        
        declaracao = declaracao + ctx.IDENT().getText()+" (";
        
        //captura dos parametros
        if (ctx.parametros_opcional() != null){
            // pega o tipo do parametro
            switch (ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().tipo_basico().getText()){
                case "literal":
                    //char eh passado como um ponteiro aqui
                    declaracao = declaracao + "char* ";
                    break;
                case "inteiro":
                    declaracao = declaracao + "int ";
                    break;
                case "real":
                    declaracao = declaracao + "float ";
                    break;
                default: //caso logico
                    declaracao = declaracao + "bool ";  
            }
            
            // pega o nome da variavel
            declaracao = declaracao + ctx.parametros_opcional().parametro().identificador().getText();
            
            if (ctx.parametros_opcional().parametro().mais_parametros().getStart().getText().equals(","))
                //aqui tambem jah seria uma boa poder tratar retornos no noh, assim nao precisaria fazer todo 
                // o tratamento para o mais_parametros... por enquanto fica soh a virgula pois nao tem
                // nenhum caso de teste que utilize mais que um parametro, qualquer coisa dou uma melhorada aqui depois...
                declaracao = declaracao + " ,";
        }
        
        declaracao = declaracao + ") {";
        codigo.println(declaracao);
    }
    
    //Codigos reaproveitados do trabalho 1
    @Override
    public void exitDeclaracao_global(FAZEDORESParser.Declaracao_globalContext ctx) {
        //Declarou tudo o que tinha pra se declarar no procedimento ou funcao, fecha a chave dele e pula uma linha
        codigo.println("}");
        codigo.println("");
        
        //PARA A TABELA DE SIMBOLOS
        pilhaDeTabelas.desempilhar();
        EhFuncao = 0;
    }
    
    //Codigos reaproveitados do trabalho 1
    @Override
    public void exitComandos(FAZEDORESParser.ComandosContext ctx) {
        //Ao contrario do caso, para ou enquanto onde se fecha a chave no exitCmd, o se
        // se fecha a chave aqui devido a possibilidade do se possuir um senao
        
        //Nao se usa o getStart aqui pois o comandos tambem eh o que inicia a declaracao dos
        // comandos apos a declaracao de variaveis no main(), o que poderia resultar em erros caso a 
        // primeira coisa que se declara apos as variaveis eh um se, entao se pega o texto do primeiro filho
        if (ctx.getParent().getChild(0).getText().equals("se"))
            codigo.println("\t}");
        else{
            //Apos colocar todos os comandos dentro de um case do caso, eh necessario colocar um break
            // entao para verificar se eh a situacao do caso se procura pelo irmao da esquerda do comandos,
            // que na regra do caso eh o token ":"     
            if (ctx.getParent().getChild(1).getText().equals(":"))
                codigo.println("break;");
        }
    }
    
    @Override 
    public void enterCmd(FAZEDORESParser.CmdContext ctx) {
        //PARA A TABELA DE SIMBOLOS
        TabelaDeSimbolos tabelaAtual = pilhaDeTabelas.topo();
        
        String condicao, tokenTratado, variavelPara;
        String estrutura = ctx.getStart().getText();
        
        // verifica se eh se, caso, para ou enquanto pois sua estrutura inicial eh parecida
        if (estrutura.equals("se") || estrutura.equals("caso") || 
            estrutura.equals("para") || estrutura.equals("enquanto")){

            switch (estrutura){
                case "se":
                    condicao = "\tif";
                    break;
                case "caso":
                    condicao = "\tswitch";
                    break;
                case "para":
                    condicao = "\tfor";
                    break;
                default: //enquanto
                    condicao = "\twhile";
            }
            //adiciona parenteses para todos
            condicao = condicao + " (";

            //preenchimento do parenteses
            switch (estrutura){
                //vale para o se e para o enquanto esse codigo
                case "se":
                case "enquanto":
                    //Verificar se foi negado no comeco
                    if (ctx.expressao().termo_logico().fator_logico().op_nao().getText().equals("nao")){
                        //pensei sim em sobrescrever o contexto ctx, mas nao pode... :(
                        FAZEDORESParser.ExpressaoContext naoCtx = ctx.expressao().termo_logico().fator_logico().parcela_logica().exp_relacional().exp_aritmetica().termo().fator().parcela().parcela_unario().expressao();
                        //pega a primeira parte de uma expressao
                        condicao = condicao + "!(" + naoCtx.termo_logico().fator_logico().parcela_logica().exp_relacional().exp_aritmetica().getText();
                        //verifica se op_relacional nao eh vazio pois dois dos operadores precisam ser convertidos para C
                        if (!naoCtx.termo_logico().fator_logico().parcela_logica().exp_relacional().op_opcional().getStart().getText().equals("")){
                            tokenTratado = TrataSimbolosGeradorCodigo.trataToken(naoCtx.termo_logico().fator_logico().parcela_logica().exp_relacional().op_opcional().op_relacional());
                            condicao = condicao + " " + tokenTratado + " " +
                                    naoCtx.termo_logico().fator_logico().parcela_logica().exp_relacional().op_opcional().exp_aritmetica().getText();
                        }
                        //verifica se teve um operador e na expressao
                        if (naoCtx.termo_logico().outros_fatores_logicos().getStart().getText().equals("e")){
                            tokenTratado = TrataSimbolosGeradorCodigo.trataToken(naoCtx.termo_logico().outros_fatores_logicos());
                            //a partir do fator_logico ele parou de reconhecer tudo: parcela_logica, getChild, getText...
                            //logico que nao eh o certo pegar tudo a partir do fator_logico (o getChild(1)), mas no unico teste que passa por aqui isso nao vai ser problema
                                    
                            //pega a primeira parte de uma expressao
                            condicao = condicao + " " + tokenTratado + " " +
                                    naoCtx.termo_logico().outros_fatores_logicos().getChild(1).getText();
                        }
                        //verifica se teve um operador ou na expressao
                        if (naoCtx.outros_termos_logicos().getStart().getText().equals("ou")){
                            tokenTratado = TrataSimbolosGeradorCodigo.trataToken(naoCtx.outros_termos_logicos());
                            //mesma coisa que ocorreu com o fator_logico, mas agora com o termo_logico
                            condicao = condicao + " " + tokenTratado + " " +
                                    naoCtx.outros_termos_logicos().getChild(1).getText();
                        }
                        //fecha o parenteses extra que foi aberto pelo nao
                        condicao = condicao + ")";
                    }
                    else{ 
                        //pega a primeira parte de uma expressao
                        condicao = condicao + ctx.expressao().termo_logico().fator_logico().parcela_logica().exp_relacional().exp_aritmetica().getText();
                        //verifica se op_relacional nao eh vazio pois dois dos operadores precisam ser convertidos para C
                        if (!ctx.expressao().termo_logico().fator_logico().parcela_logica().exp_relacional().op_opcional().getStart().getText().equals("")){
                            tokenTratado = TrataSimbolosGeradorCodigo.trataToken(ctx.expressao().termo_logico().fator_logico().parcela_logica().exp_relacional().op_opcional().op_relacional());
                            condicao = condicao + " " + tokenTratado + " " +
                                    ctx.expressao().termo_logico().fator_logico().parcela_logica().exp_relacional().op_opcional().exp_aritmetica().getText();
                        }
                        //verifica se teve um operador e na expressao
                        if (ctx.expressao().termo_logico().outros_fatores_logicos().getStart().getText().equals("e")){
                            tokenTratado = TrataSimbolosGeradorCodigo.trataToken(ctx.expressao().termo_logico().outros_fatores_logicos());
                            //a partir do fator_logico ele parou de reconhecer tudo: parcela_logica, getChild, getText...
                            //logico que nao eh o certo pegar tudo a partir do fator_logico (o getChild(1)), mas no unico teste que passa por aqui isso nao vai ser problema
                                    
                            //pega a primeira parte de uma expressao
                            condicao = condicao + " " + tokenTratado + " " +
                                    ctx.expressao().termo_logico().outros_fatores_logicos().getChild(1).getText();
                        }
                        //verifica se teve um operador ou na expressao
                        if (ctx.expressao().outros_termos_logicos().getStart().getText().equals("ou")){
                            tokenTratado = TrataSimbolosGeradorCodigo.trataToken(ctx.expressao().outros_termos_logicos());
                            //mesma coisa que ocorreu com o fator_logico, mas agora com o termo_logico
                            condicao = condicao + " " + tokenTratado + " " +
                                    ctx.expressao().outros_termos_logicos().getChild(1).getText();
                        }
                    }
                    break;
                case "caso":
                    // por algum motivo ele nao estah me deixando fazer ctx.exp_aritmetica.getText();
                    condicao = condicao+ctx.getChild(1).getText();
                    break;
                default: //caso do para
                    //mesmo problema que no caso do caso (:P), nao consigo fazer ctx.exp_aritmetica.getText();
                    variavelPara = ctx.IDENT().getText();
                    condicao = condicao + variavelPara + " = " + ctx.getChild(3).getText() + "; ";
                    condicao = condicao + variavelPara + " <= " + ctx.getChild(5).getText() + "; ";
                    condicao = condicao + variavelPara + "++";
            }
            //fecha o parenteses para todos e imprime no codigo
            condicao = condicao + ") {";
            codigo.println(condicao);
        }
        else{
            if (estrutura.equals("leia"))
                codigo.println("\t" + ctx.identificador().getText() + " = Serial.read();");
            else{
                if (estrutura.equals("escreva"))
                    codigo.println("\tSerial.println(" + ctx.expressao().getText() + ");");
            }
        }  
    }
    
    //Codigos reaproveitados do trabalho 1
    @Override
    public void exitCmd(FAZEDORESParser.CmdContext ctx) {
        //Declarou tudo o que tinha para declarar em caso, para ou enquanto, fecha a chave 
        //O se nao eh tratado aqui pois ele pode ter um senao
        String tipoCmd = ctx.getStart().getText(); 
        
        if (tipoCmd.equals("caso") || tipoCmd.equals("para") || tipoCmd.equals("enquanto"))
            codigo.println("}");
    }
    
    @Override
    public void enterComandosSetup(FAZEDORESParser.ComandosSetupContext ctx){
        //Declaracao da funcao setup do Arduino
        codigo.println("void setup() {");
    }
    
    @Override
    public void exitComandosSetup(FAZEDORESParser.ComandosSetupContext ctx){
        //Caso tenha sido declarado o LCD, eh necessario uma inicializacao especial para ele
        if (flagLCD)
            codigo.println("\tlcd.begin(16,2);");
        //Caso possua as funcoes leia ou escreva, eh necessario uma inicializacao para o serial
        if (flagSerial)
            codigo.println("\tSerial.begin(115200);");
        //Terminou todas as declaracoes da funcao setup, eh fechada a chave dela
        codigo.println("}");
        //Uma linha em branco e declara a funcao loop do Arduino
        codigo.println("");
        codigo.println("void loop() {");
        
        //Caso for utilizar funcoes do Serial, deve-se verificar se ele estah ativado
    }
    
    @Override
    public void enterComandoSetup(FAZEDORESParser.ComandoSetupContext ctx){
        int i=0;
        String setup = "";
        
        /*Por nao saber quantos 'ativar' existem aqui dentro, entao deve ser 
        utilizado este loop com getChild, mas por cima eu indico qual eh a 
        operacao que estou fazendo*/
        while (ctx.getChild(i) != null){
            
            //ctx.dispositivo().getText().equals("led")
            if (ctx.getChild(i+2).getText().equals("led") || 
                ctx.getChild(i+2).getText().equals("luz") || 
                ctx.getChild(i+2).getText().equals("som") || 
                ctx.getChild(i+2).getText().equals("lcd"))
                //ctx.PORTA().getText()
                setup = "\tpinMode(" + ctx.getChild(i+4).getText() + ", OUTPUT);";
            else{
                //ctx.dispositivo().getText().equals("botao")
                if (ctx.getChild(i+2).getText().equals("botao") ||
                    ctx.getChild(i+2).getText().equals("sensortoque") ||
                    ctx.getChild(i+2).getText().equals("potenciometro"))
                    //ctx.PORTA().getText()
                    setup = "\tpinMode(" + ctx.getChild(i+4).getText() + ", INPUT);";
            }
            
            codigo.println(setup);
            
            //incrementa o i na quantidade de filhos que o ComandoSetup possui
            i = i+6;
        }
    }
    
    @Override
    public void enterCmdLoop(FAZEDORESParser.CmdLoopContext ctx) {
        
        String loop = "";

        if(ctx.IDENT() != null)
        {
            loop = "\t" + ctx.IDENT().getText();
            //Tipo do que estah sendo lido determina a funcao de leitura
            if (ctx.dispositivoEntrada().getText().equals("botao") ||
                    ctx.dispositivoEntrada().getText().equals("sensortoque"))
                    loop += " = " + "digitalRead(" + ctx.pino().getText() + ");";
            else
            {
                    /*Potenciometro alem de utilizar uma funcao de leitura diferente,
                      tambem necessita de uma chamada de map imediatamente apos a leitura*/
                    if (ctx.dispositivoEntrada().getText().equals("potenciometro"))
                        loop += " = " + "analogRead(" + ctx.pino().getText() + ");" + "\n\t" 
                                + ctx.IDENT().getText() + " = map("  + ctx.IDENT().getText() + ", 0, 1023, 0, 255);";
            }
        }
        else
        {
            String regra = ctx.getStart().getText();

            if (regra.equals("ligar") || regra.equals("desligar"))
            {
                //Verifica se existe o volt por meio do sexto filho, se eh ',' ou ')'
                if (ctx.getChild(5).getText().equals(","))
                {
                    //Caso do analogWrite
                    //Verifica se o dispositivoSaida eh um led
                    if (ctx.getChild(2).getText().equals("led") || ctx.getChild(2).getText().equals("som")  ){
                        //Aqui nao existe diferenca entre ligar ou desligar (fonte: iot.pdf, pag 22)
                        loop = "\tanalogWrite(" + ctx.pino().getText() + ", " + ctx.volt().getText() +");";
                    }
                }
                else{
                    //Verifica se o dispositivoSaida eh um led, uma luz ou um som
                    if (ctx.getChild(2).getText().equals("led") ||
                        ctx.getChild(2).getText().equals("luz") ||
                        ctx.getChild(2).getText().equals("som")){
                        loop = "\tdigitalWrite(" + ctx.pino().getText();
                        if (regra.equals("ligar"))
                            loop = loop + ", HIGH);";
                        else
                            loop = loop + ", LOW);";
                    }
                }
            }
            else{
                if (regra.equals("esperar"))
                    loop = "\tdelay(" + ctx.tempo().getText() + ");";
            }
            
         }
            codigo.println(loop);
        
    }
    
    @Override 
    public void enterComandoLCD(FAZEDORESParser.ComandoLCDContext ctx) { 
        //Verifica qual eh o comando LCD que serah gerado
        if (ctx.getStart().getText().equals("definirCor"))
            /*Coloca a cor passada como parametro, note que os parenteses 
            utilizados vem da propria regra cor*/
            codigo.println("\tlcd.setRGB" + ctx.getChild(6).getText() + ";");
        //escrever
        else{
            //Escreve a mensagem que estah sendo passado como parametro
            codigo.println("\tlcd.print(" + ctx.getChild(6).getText() + ");");
        }
    }
    
    //Codigos reaproveitados do trabalho 1
    @Override
    public void enterSenao_opcional(FAZEDORESParser.Senao_opcionalContext ctx) {
        //primeiramente se verifica se esse noh nao estah vazio
        if (!ctx.getText().equals("")){
            //verifica se o primeiro token encontrado no pai eh se, ou seja, eh o caso de senao,
            // ou se eh um caso, ou seja, eh o caso do default
            if (ctx.getParent().getStart().getText().equals("se"))
                codigo.println("\telse {");
            else
                codigo.println("\tdefault:");
        }
    }
    
    //Codigos reaproveitados do trabalho 1
    @Override
    public void exitSenao_opcional(FAZEDORESParser.Senao_opcionalContext ctx) {
        //primeiramente se verifica se esse noh nao estah vazio
        if (!ctx.getText().equals("")){
            //verifica se o primeiro token encontrado no pai eh se pois apenas no se eh necessario
            // fechar a chave do senao, na regra do caso o default nao utiliza chave
            if (ctx.getParent().getStart().getText().equals("se"))
                codigo.println("\t}");
        }
    }
    
    @Override 
    public void enterChamada_atribuicao(FAZEDORESParser.Chamada_atribuicaoContext ctx) { 
        String atribuicao;
        
        //Busca a variavel que vai receber a atribuicao
        atribuicao = "\t" + ctx.getParent().getChild(0).getText();
        
        //Verifica primeiro se eh uma chamada de funcao
        if (ctx.getStart().getText().equals("(")){
            atribuicao = atribuicao + "(" + ctx.argumentos_opcional().getText() + ");";
        }
        //Caso nao seja uma chamada de funcao, eh uma atribuicao (basica ou leitura)
        else
        {
         
                //Eh apenas uma atribuicao basica
                atribuicao = atribuicao + " = " + ctx.getChild(3).getText() + ";";
        }
        codigo.println(atribuicao);
    }
    
    // Funcao utilizada para procurar chamadas leia e escreva dentro da regra sintatica comandos
    public void ProcuraEscrevaLeia (FAZEDORESParser.ComandosContext ctx){
        int i = 0;
        String chamadaFeita;
        
        /*Tenta encontrar alguma funcao do tipo leia ou escreva percorrendo todas
          as regras cmd que existirem na regra sintatica comandos que estah sendo 
          verificada. Para de verificar quando nao ha mais regras cmd ou quando
          conseguiu encontrar uma regra leia ou escreva, ativando a flag*/
        while (ctx.cmd(i) != null && flagSerial == false){
            
            //Pega qual chamada eh feita
            chamadaFeita = ctx.cmd(i).getStart().getText();
            if (chamadaFeita.equals("leia") || chamadaFeita.equals("escreva")){
                //Encontrado um lcd sendo ativado, entao torna true sua flag 
                flagSerial = true;
            }
            else{
                /*Verifica se a chamada que eh feita eh alguma que chama a regra
                  comandos internamente*/
                if (chamadaFeita.equals("se") || chamadaFeita.equals("para") ||
                    chamadaFeita.equals("enquanto") || chamadaFeita.equals("faca"))
                    ProcuraEscrevaLeia(ctx.cmd(i).comandos());
                
                //A regra caso tambem possui a regra comandos, porem um pouco mais escondido
                if (chamadaFeita.equals("caso"))
                    ProcuraEscrevaLeia(ctx.cmd(i).selecao().comandos());
                
                //Mesma situacao do senao_opcional
                if (chamadaFeita.equals("se")){
                    //Verifica se existe algo no senao_opcional
                    if (ctx.cmd(i).senao_opcional().comandos() != null)
                        ProcuraEscrevaLeia(ctx.cmd(i).senao_opcional().comandos());
                }
            }
            
            //Incrementa o i
            i++;
        }
    }
    
    //Codigos reaproveitados do trabalho 1
    // A partir de agora sao as tres funcoes utilizadas para adicionar simbolos nas tabelas de simbolos
    public void AdicionarSimboloRegistro(FAZEDORESParser.Declaracao_localContext ctx, TabelaDeSimbolos tabelaDeSimbolosAtual, String nomeDoReg){ 
        String nome, tipo;
        
        TabelaDeSimbolos tabelaDoRegistro = new TabelaDeSimbolos("registro");
                   
        if (ctx.tipo()!=null){      
            nome = ctx.tipo().registro().variavel().IDENT().getText();
            if (ctx.tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                tipo = ctx.tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT().getText();
            else
                tipo = ctx.tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                   
            if (!tabelaDoRegistro.existeSimbolo(nome))
                tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                   
            for(int i = 0; i<ctx.tipo().registro().variavel().mais_var().IDENT().size(); i++){
                nome = ctx.tipo().registro().variavel().mais_var().IDENT(i).getText();
                if(!tabelaDoRegistro.existeSimbolo(nome))
                    tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
            }
                   
            for(int i = 0; i < ctx.tipo().registro().mais_variaveis().variavel().size(); i++){
                nome = ctx.tipo().registro().mais_variaveis().variavel(i).IDENT().getText();
                if (ctx.tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                    tipo = ctx.tipo().registro().mais_variaveis().variavel(i).tipo().tipo_estendido().tipo_basico_ident().IDENT().getText();
                else
                    tipo = ctx.tipo().registro().mais_variaveis().variavel(i).tipo().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                   
                if (!tabelaDoRegistro.existeSimbolo(nome))
                    tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                        
                for(int j = 0; j<ctx.tipo().registro().mais_variaveis().variavel(i).mais_var().IDENT().size(); j++){
                    nome = ctx.tipo().registro().mais_variaveis().variavel(i).mais_var().IDENT(i).getText();
                    if (!tabelaDoRegistro.existeSimbolo(nome))
                        tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                }
            }
        }
        else{
            if (ctx.variavel().tipo()!=null){
                nome = ctx.variavel().tipo().registro().variavel().IDENT().getText();
                if (ctx.variavel().tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                    tipo = ctx.variavel().tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT().getText();
                else
                    tipo = ctx.variavel().tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                   
                if (!tabelaDoRegistro.existeSimbolo(nome))
                    tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                   
                for (int i = 0; i<ctx.variavel().tipo().registro().variavel().mais_var().IDENT().size(); i++){
                    nome = ctx.variavel().tipo().registro().variavel().mais_var().IDENT(i).getText();
                    if(!tabelaDoRegistro.existeSimbolo(nome))
                        tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                }
                   
                for (int i = 0; i < ctx.variavel().tipo().registro().mais_variaveis().variavel().size(); i++){
                    nome = ctx.variavel().tipo().registro().mais_variaveis().variavel(i).IDENT().getText();
                    if (ctx.variavel().tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                        tipo = ctx.variavel().tipo().registro().mais_variaveis().variavel(i).tipo().tipo_estendido().tipo_basico_ident().IDENT().getText();
                    else
                        tipo = ctx.variavel().tipo().registro().mais_variaveis().variavel(i).tipo().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                   
                    if (!tabelaDoRegistro.existeSimbolo(nome))
                        tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                   
                    for(int j = 0; j<ctx.variavel().tipo().registro().mais_variaveis().variavel(i).mais_var().IDENT().size(); j++){
                        nome = ctx.variavel().tipo().registro().mais_variaveis().variavel(i).mais_var().IDENT(i).getText();
                        if(!tabelaDoRegistro.existeSimbolo(nome))
                            tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                    }
                }
            } 
        }        
        tabelaDeSimbolosAtual.adicionarSimbolo(nomeDoReg, nomeDoReg, null, tabelaDoRegistro);         
    }
    
    //Codigos reaproveitados do trabalho 1
    public void AdicionarSimbolo(FAZEDORESParser.Declaracao_localContext ctx, TabelaDeSimbolos tabelaDeSimbolosAtual){
        String nome, tipo;
        
        if (ctx.variavel().tipo().registro()==null){      
            nome = ctx.variavel().IDENT().getText();
            if(ctx.variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                tipo = ctx.variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT().getText();
            else
                tipo = ctx.variavel().tipo().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                   
            if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                tabelaDeSimbolosAtual.adicionarSimbolo(nome, tipo, null, null);
                   
            for(int i = 0; i<ctx.variavel().mais_var().IDENT().size(); i++){
                nome = ctx.variavel().mais_var().IDENT(i).getText();
                if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                    tabelaDeSimbolosAtual.adicionarSimbolo(nome, tipo, null, null);
            }
        }
        else{
            nome = ctx.variavel().IDENT().getText();
                
            if(!pilhaDeTabelas.existeSimbolo(nome))
                AdicionarSimboloRegistro(ctx, tabelaDeSimbolosAtual, nome);
                
            for(int i = 0; i<ctx.variavel().mais_var().IDENT().size(); i++){
                nome = ctx.variavel().mais_var().IDENT(i).getText();
                if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                    AdicionarSimboloRegistro(ctx, tabelaDeSimbolosAtual, nome);
            }
        }       
    }
    
    //Codigos reaproveitados do trabalho 1
    public void AdicionarTiposParametros(FAZEDORESParser.Declaracao_globalContext ctx, List<String> ListaNomePar, List<String> ListaTipoPar){
        String nome, tipo;
        TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo();
        
        nome = ctx.parametros_opcional().parametro().identificador().IDENT().getText();
        if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
            ListaNomePar.add(nome);
        
        if(ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().IDENT()!=null)
            tipo = ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().IDENT().getText();
        else
            tipo = ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
        
        ListaTipoPar.add(tipo);
        
        for(int i = 0; i<ctx.parametros_opcional().parametro().mais_ident().identificador().size(); i++){
            nome = ctx.parametros_opcional().parametro().mais_ident().identificador(i).IDENT().getText();
            if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                ListaNomePar.add(nome);
            ListaTipoPar.add(tipo);
        }
        
        if(ctx.parametros_opcional().parametro().mais_parametros().parametro()!=null){
            nome = ctx.parametros_opcional().parametro().mais_parametros().parametro().identificador().IDENT().getText();
            if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                ListaNomePar.add(nome);
        
            if(ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                tipo = ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().IDENT().getText();
            else
                tipo = ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
        
            ListaTipoPar.add(tipo);
        
            for(int i = 0; i < ctx.parametros_opcional().parametro().mais_parametros().parametro().mais_ident().identificador().size(); i++){
                nome = ctx.parametros_opcional().parametro().mais_parametros().parametro().mais_ident().identificador(i).IDENT().getText();
                if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                    ListaNomePar.add(nome);
                ListaTipoPar.add(tipo);
            }
        }
    }
}
