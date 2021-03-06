package trabalho3;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.misc.NotNull;
import trabalho3.FAZEDORESParser.Declaracao_localContext;
import trabalho3.FAZEDORESParser.DimensaoContext;

/**
 *
 * A classe AnalisadorSemantico representa a etapa de analise semantica da gramatica LL.
 */
public class AnalisadorSemantico extends FAZEDORESBaseListener {

    //Referencia a uma instancia da classe Saida
    Saida out;
    //Atributo com intuito de servir como uma flag, apenas para indicar se o codigo se trata de uma funcao
    //EhFuncao e utilizada para identificar se a palavra chave retorne foi usada em escopo não permitido
    int EhFuncao = 0;

    // Uma instancia da pilha de tabelas que armazenara as tabelas de simbolos que forem criadas para cada escopo
    static PilhaDeTabelas pilhaDeTabelas = new PilhaDeTabelas();
    
    
    
    public AnalisadorSemantico(Saida out) {
        this.out = out;
    }

    @Override
    public void enterPrograma(FAZEDORESParser.ProgramaContext ctx) {
        
        pilhaDeTabelas.empilhar(new TabelaDeSimbolos("global"));
        
        
    }
    
    /*
    * Regras semanticas:
    *
    *1 - Erros que podem ser verificados no ativar:
    * Porta utilizada não ser analogica/digital, depende do dispositivo; (deve verificar no ativar também)
    *	- 0 a 10 são portas digitais (led, som, luz, botao)
    *	- 3, 5, 3, 6 são portas PWM (led, som, luz, botao)
    *	- 10 a 20 portas analogicas (potenciometro, sensor de toque)
    *	- 21 porta I2C (usada para o LCD)
    * Portas 'ativar' repetidas;
    *
    *2 - Verificação de volt: entre 0 e 255
    *	* definição de volt só pode acontecer em portas analogicas
    *3 - Verificação das cores: entre 0 e 255
    *4 - Verificação se a porta utilizada nos comandos loop é a mesma do setup
    *5 - Verificação de uso de portas repetidas
    */
    
    
    /*O metodo enterComandosSetup foi sobrescrito do listener. Esse metodo sera visitado pelo ParserTreeWalker
    * toda vez que a regra comandosSetup for utilizada na gramatica. Essa regra e utilizada para a ativação dos
    * pinos que serão utilizados nos comandos loop. Ou seja, todo pino que for utilizado no comando loop deve 
    * inicialmente ter uma declaração equivalente no setup. Dessa forma, uma tabela de símbolos foi criada para
    * adicionar os os pinos do comando setup. Para poder guardar as informações relativas aos pinos na tabela de
    * símbolos, a entrada da tabela de símbolos conta com dois campos a mais: valor (que se refere ao número do pino)
    * e dispositivo, que refere-se ao dispositivo associado ao pino. O tipo do pino é determinado por seu número e 
    * é armazenado no campo tipo (que para variáveis e constantes armazenava o tipo delas). É importante frisar que
    * quando uma constante é declarada, ela possui um valor associado. Assim, se, ao invés de ter um número no campo
    * que especifica um pino existir um nome de uma constante, o valor associado a essa constante será pesquisado
    * na tabela de símbolos para determinar o valor do pino.
    */
    @Override
    public void enterComandosSetup(FAZEDORESParser.ComandosSetupContext ctx)
    {
        //criação de uma tabela de simbolos específica para o setup
        TabelaDeSimbolos tabelaDeSimbolosSetup = new TabelaDeSimbolos("setup");
        pilhaDeTabelas.empilhar(tabelaDeSimbolosSetup);
        TabelaDeSimbolos tabelaAtual = pilhaDeTabelas.topo();
        
        for(int i = 0; i < ctx.comandoSetup().size(); i++)
        {
            //para cada comando ativar, os pinos são verificados
            if(ctx.comandoSetup(i).getStart().getText().equals("ativar"))
            {
                //Se for um número, esse número é recuperado
                if(ctx.comandoSetup(i).pino().NUM_INT() != null)
                {
                    String numPino = ctx.comandoSetup(i).pino().getText();
                    int linha = ctx.comandoSetup(i).pino().NUM_INT().getSymbol().getLine();
                    if(!tabelaAtual.existePino(numPino))
                    {
                        //A string que é recuperada é convertida para int, para as verificações
                        int num = Integer.parseInt(numPino);
                        //o dispositivo é recuperado
                        String dispositivo = ctx.comandoSetup(i).dispositivo().getText();
                        //o tipo da porta é verificado a partir do número dessa porta, através da função verificaValoresPinos
                        String tipoPorta = verificaValoresPinos(num);
                        //O método verificaErrosPinos verifica os erros que podem acontecer de algum pino ser associado com
                        //algum dispositivo incorreto
                        verificaErrosPinos(dispositivo, linha, tipoPorta);
                        //depois é adicionado a tabela do setup
                        tabelaAtual.adicionarSimbolo(tipoPorta, numPino, dispositivo);
                    }else
                    {
                        out.println("Linha "+ linha+": Esse pino já foi ativado anteriormente.");
                    }
                }else{
                    //Se for um identificador, esse nome tem que estar presente na tabela de símbolos, e, se for uma constante
                    // ele deve ter um valor associado a ele, que será recuperado da tabela
                    if(ctx.comandoSetup(i).pino().IDENT()!=null)
                    {
                        String nome = ctx.comandoSetup(i).pino().getText();
                        int linha = ctx.comandoSetup(i).pino().IDENT().getSymbol().getLine();
                        
                        if(!pilhaDeTabelas.existeSimbolo(nome))
                        {
                            out.println("Linha "+linha+": Um nome deve ser declarado antes de ser usado.");
                        }else
                        {
                            //trata-se de uma constante. Valor é inicializado apenas quando se trata de uma constante
                            if(pilhaDeTabelas.getValor(nome)!=null)
                            {
                                String valor = pilhaDeTabelas.getValor(nome);
                                if(!tabelaAtual.existePino(valor))
                                {
                                    //A string que é recuperada é convertida para int, para as verificações
                                    int num = Integer.parseInt(valor);
                                    //o dispositivo é recuperado
                                    String dispositivo = ctx.comandoSetup(i).dispositivo().getText();
                                    //o tipo da porta é verificado a partir do número dessa porta, através da função verificaValoresPinos
                                    String tipoPorta = verificaValoresPinos(num);
                                    //O método verificaErrosPinos verifica os erros que podem acontecer de algum pino ser associado com
                                    //algum dispositivo incorreto
                                    verificaErrosPinos(dispositivo, linha, tipoPorta);
                                    //depois é adicionado a tabela do setup
                                    tabelaAtual.adicionarSimbolo(tipoPorta, valor, dispositivo);
                                }else{
                                    out.println("Linha "+linha+": Esse pino já foi ativado anteriormente.");
                                }
                                
                                
                            }
                        }
                       
                        
                    }
                }
                
            }
            
        }
    }
    
    /*O metodo enterComandoLoop foi sobrescrito do listener. Esse metodo será visitado pelo ParserTreeWalker
    * toda vez que a regra comandosSetup for utilizada na gramatica. Essa regra é responsável por indicar os 
    * comandos do arduindo e equivale à função loop, de forma que todo pino que estiver sendo usado deve 
    * possuir uma incialização equivalente no comandosSetup. Sendo assim, a tebela do Setup é utilizada para verificar
    * se o pino já foi declarado. A tabela é encontrada a partir da busca pelo nome de seu escopo. Apesar de que na 
    * nossa gramática o comandosLoop venha imediatamente após o comandosSetup, e dessa forma a tabela do topo seria
    * a tabela equivalente a tabela setup, preferimos deixar essa busca dessa forma para ficar explícito de que a 
    * tabela que se deve verificar as ativações é a tabela do setup. Outras verificaçõs semânticas são realizadas nesse
    * método, como o valor da voltagem, se a voltagem está especificada com o dispositivo/porta correto, e verificações
    * nos valores das cores.
    */
    
    
    @Override
    public void enterComandoLoop(FAZEDORESParser.ComandoLoopContext ctx)
    {
        //recupera a tabela do setup
        TabelaDeSimbolos tabelaDoSetup = pilhaDeTabelas.getTabela("setup");
        int linha = 0;
        String dispositivo = "";
        String dispositivoDeclarado;
        int num = 0;
        String numPino = "";
        
        if(tabelaDoSetup != null)
        {
            //para cada comandoLoop
            for(int i = 0; i < ctx.cmdLoop().size(); i++)
            {
                //comaçando verificando o dispositivo LCD, que tem algumas regras específicas
                if(ctx.cmdLoop(i).comandoLCD()!=null)
                {
                    //determinando o dispositivo
                    dispositivo = "lcd";
                    //recuperando o pino
                    String pino = ctx.cmdLoop(i).comandoLCD().pino().getText();
                    //recuperando o valor, seja do número ou do valor declarado de uma constante
                    if(ctx.cmdLoop(i).comandoLCD().pino().IDENT()!=null)
                    {
                        numPino = pilhaDeTabelas.getValor(pino);
                    }else{
                        numPino = pino;
                    }
                    num = Integer.parseInt(numPino);
                    linha = ctx.cmdLoop(i).comandoLCD().getStop().getLine();
                    String tipoPorta = verificaValoresPinos(num);
                    //se não existir o pino, a mensagem de que esse pino deve ser ativado é impressa
                    if(!tabelaDoSetup.existePino(numPino))
                    {
                        out.println("Linha "+linha+": porta nao ativada");
                        out.println("Dica: lembre-se de usar o comando ativar(dispositivo, pino) nos comandos setup.");
                    }

                    //erros nos pinos em relação ao dispositivo são verificados, assim como no setup
                    verificaErrosPinos(dispositivo, linha, tipoPorta);

                    //Se a cor for definida, é necessário verificar se sua especificação segue o padrão RGB,
                    //em que cada componente varia de 0 a 255
                    if(ctx.cmdLoop(i).comandoLCD().cor()!=null)
                    {
                        String numInt1 = ctx.cmdLoop(i).comandoLCD().cor().NUM_INT(0).getText();
                        String numInt2 = ctx.cmdLoop(i).comandoLCD().cor().NUM_INT(1).getText();
                        String numInt3 = ctx.cmdLoop(i).comandoLCD().cor().NUM_INT(2).getText();

                        int num1 = Integer.parseInt(numInt1);
                        int num2 = Integer.parseInt(numInt2);
                        int num3 = Integer.parseInt(numInt3);

                        if(((num1 > 255) || (num1 < 0)) || ((num2 > 255) || (num2 < 0)) || ((num3 > 255) || (num3 < 0)))
                        {
                            out.println("Linha "+linha+": cor definida incorretamente");
                            out.println("Dica: a cor deve ser definida da seguinte forma (a, b, c), onde a corresponde ao R (de 0 a 255), o b ao B (de 0 a 255), e o c ao G (de 0 a 255) do sistema de cores RGB");
                        }

                    }


                }else{    
                    //Se não for LCD, as verificações também são realizadas
                if(ctx.cmdLoop(i).pino()!=null)
                {   String pino = ctx.cmdLoop(i).pino().getText();
                    //se for ligar ou desligar, o dispositivo é um dispositivo de saída
                    if(ctx.cmdLoop(i).getStart().getText().equals("ligar")||ctx.cmdLoop(i).getStart().getText().equals("desligar"))
                    {
                        dispositivo = ctx.cmdLoop(i).dispositivoSaida().getText();
                    }else{
                        //se for ler, é de entrada
                        if(ctx.cmdLoop(i).IDENT() != null)
                        {
                            dispositivo = ctx.cmdLoop(i).dispositivoEntrada().getText();
                        }
                    }    

                    //O número do pino é recuperado da mesma forma no ativar, ou através do número inteiro
                    //ou através da tabela de símbolos, recuperando o valor associado ao nome da constante
                    if(ctx.cmdLoop(i).pino().NUM_INT()!=null)
                    {   
                        num = Integer.parseInt(pino);
                        linha = ctx.cmdLoop(i).pino().NUM_INT().getSymbol().getLine();
                        numPino = pino;
                    }else{
                        if(ctx.cmdLoop(i).pino().IDENT()!=null)
                        {
                            linha = ctx.cmdLoop(i).pino().IDENT().getSymbol().getLine();
                            numPino = pilhaDeTabelas.getValor(pino);
                            if (numPino!= null)
                            {
                                num = Integer.parseInt(numPino);


                            }
                            else
                            {
                                num = -1;
                                out.println("Linha "+linha+" pino "+pino+" não declarado.");
                            }
                        }
                    }        

                    //o dispositivo que foi declarado também é recuperado de acordo com o número do pino,
                    //para verificar se o nome do dispositivo declarado em relação ao pino é equivalente 
                    //ao dispositivo usado nesse pino no comando
                    dispositivoDeclarado = tabelaDoSetup.getDispositivo(numPino);
                    //primeiramente, verifica se o pino foi declarado
                    if(!tabelaDoSetup.existePino(numPino))
                    {
                        out.println("Linha "+linha+": porta nao ativada");
                        out.println("Dica: lembre-se de usar o comando ativar(dispositivo, pino) nos comandos setup.");
                    }else{
                        //depois, se os dispositivos são equivalentes
                        if(!dispositivo.equals(dispositivoDeclarado))
                        {
                            out.println("Linha "+linha+": A porta "+numPino+" foi declarada associada ao dispositivo "+ 
                            dispositivoDeclarado+ " e usada com o dispositivo "+dispositivo); 
                        }
                    }

                    String tipoPorta = verificaValoresPinos(num);
                    verificaErrosPinos(dispositivo, linha, tipoPorta);

                    //Outra verificação realizada é em relação a voltagem. Primeiramente, deve-se verificar
                    //se o uso do volt é realizado em pinos (portas) corretos. As portas são analógica e pwm

                    if(ctx.cmdLoop(i).volt()!=null)
                    {

                        if(!(tipoPorta.equals("pwm") || tipoPorta.equals("portaAnalogica")))
                        {
                            out.println("Linha "+ linha +": uso de volt em portas que não são analogicas nem PWM");
                        }else{
                            //Se a porta está correta, outra verificação que deve ser feita é se a voltagem está no range
                            //permitido
                            String svolt = ctx.cmdLoop(i).volt().getText();
                            int voltagem = 0;

                                if(ctx.cmdLoop(i).volt().IDENT()!=null)
                                {
                                    String val =  pilhaDeTabelas.getValor(svolt);
                                    if(val==null)
                                    {
                                        //se for uma variavel, um aviso
                                        //out.println("Linha "+linha+": Aviso - Você está usando uma variável em um campo que deve variar de 0 a 255");
                                    }else{
                                        //Se for constante, recupera o valor
                                        voltagem = Integer.parseInt(val);
                                    }
                                }else{
                                    voltagem = Integer.parseInt(svolt);
                                }


                                        // Se a voltagem não estiver entre 0 e 255, erro semântico
                                if((voltagem < 0) || (voltagem > 255))
                                {
                                    out.println("Linha "+linha+": voltagem especificada incorreta");
                                    out.println("Dica: a voltagem varia de 0 a 255");
                                }
                            }
                        }
                        }







                }  
            }
        }
        else
        {
            out.println("O setup deve ser declarado antes.");
            out.println("Dica: programas em arduino dependem das funções setup (que é executada uma vez) e loop (que como o nome diz fica em loop).");
        }
  
    }
    

    /*
    * Método auxiliar que recebe um número de um pino e retorna o tipo do pino equivalente, seguindo a
    * seguinte regra:
    * - 0 a 10 são portas digitais 
    * - 3, 5, 3, 6 são portas PWM 
    * - 10 a 20 portas analogicas 
    * - 21 porta I2C 
    */
    public String verificaValoresPinos(int num)
    {
        if((num == 3) || (num == 6) || (num == 5))
        {
            return "pwm";
        }else{
            if((num < 10) && (num > 0))
            {
                return "portaDigital";
            }else{
                if((num > 10) || (num < 20))
                {
                    return "portaAnalogica";
                }else{
                    if(num == 21)
                    {
                        return "I2C";
                    }
                }
            }
        }
        return null;
    }
    /*
    * Método auxiliar para imprimir uma mensagem
    */
    public void printMensagem(int linha)
    {
        out.println("Linha "+ linha +": uso de porta indevida");
        out.println("Dica:");
        out.println("- 0 a 10 são portas digitais");
        out.println("- 3, 5, 3, 6 são portas PWM");
        out.println("- 10 a 20 portas analogicas");
        out.println("- 21 porta I2C (usada para o LCD)");
    }
    
    
    /*
    * Método que verifica a partir do dispositivo passado como parâmetro e o tipo da Porta. As euivalências
    * são as seguintes:
    * portas digitais (led, som, luz, botao)
    * portas PWM (led, som, luz, botao)
    * portas analogicas (potenciometro, sensor de toque)
    * porta I2C (usada para o LCD)
    */
    public void verificaErrosPinos(String dispositivo, int linha, String tipoPorta)
    {
        
        switch (dispositivo){
            case "led":
                if(tipoPorta.equals("portaAnalogica"))
                {
                    printMensagem(linha); 
                }else{
                    if(tipoPorta.equals("I2C"))
                    {
                        printMensagem(linha);
                    }
                }
                break;
            case "potenciometro":
                if(tipoPorta.equals("portaDigital"))
                {
                    printMensagem(linha); 
                }else{
                    if(tipoPorta.equals("I2C"))
                    {
                        printMensagem(linha);
                    }else{
                        if(tipoPorta.equals("pwm"))
                        {
                            printMensagem(linha);
                        }
                    }
                }
                break;
            case "som":
                if(tipoPorta.equals("portaAnalogica"))
                {
                    printMensagem(linha); 
                }else{
                    if(tipoPorta.equals("I2C"))
                    {
                        printMensagem(linha);
                    }
                }
                break;
            case "luz":
                if(tipoPorta.equals("portaAnalogica"))
                {
                    printMensagem(linha); 
                }else{
                    if(tipoPorta.equals("I2C"))
                    {
                        printMensagem(linha);
                    }
                }
                break;
            case "botao":
                if(tipoPorta.equals("portaAnalogica"))
                {
                    printMensagem(linha); 
                }else{
                    if(tipoPorta.equals("I2C"))
                    {
                        printMensagem(linha);
                    }
                }
                break;
            case "sensor de toque":
                if(tipoPorta.equals("portaDigital"))
                {
                    printMensagem(linha); 
                }else{
                    if(tipoPorta.equals("I2C"))
                    {
                        printMensagem(linha);
                    }else{
                        if(tipoPorta.equals("pwm"))
                        {
                            printMensagem(linha);
                        }
                    }
                }
                break;
            case "LCD":
                if(tipoPorta.equals("portaDigital"))
                {
                    printMensagem(linha); 
                }else{
                    if(tipoPorta.equals("portaAnalogica"))
                    {
                        printMensagem(linha);
                    }else{
                        if(tipoPorta.equals("pwm"))
                        {
                            printMensagem(linha);
                        }
                    }
                }
                break;
        }
                
        
    }
    
    /************************************************************************************************************
     * **********************************************************************************************************
     * Parte da análise semântica referente a linguagem LA. Foi reaproveitada do trabalho1.
     */
    
    /*O metodo enterDeclaracao_local foi sobrescrita do listener. Esse metodo sera visitado pelo ParserTreeWalker
    * toda vez que a regra declaracao_local for utilizada na gramatica. Essa regra e usada para inicializacao de
    * variaveis, constantes e declaracao de registros.
    */
    @Override
    public void enterDeclaracao_local(Declaracao_localContext ctx) {
        TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo();
        
        String nome;
        String tipo;
        int linha;
        String valor;
        
        //Primeira verificacao, se a regra comeca com a palavra chave constante
        if(ctx.getStart().getText().equals("constante"))
        {
            //recupera o nome a partir do valor de texto da regra lexica IDENT
            nome = ctx.IDENT().getText();
            //a linha e recuperada pelo metodo getLine()
            linha = ctx.IDENT().getSymbol().getLine();
            // o tipo e identificado pela chamada da regra tipo_basico, recuperando o texto
            tipo = ctx.tipo_basico().getText();
            
            valor = ctx.valor_constante().getText();
                
            //Poder adicionar essa constante, primeiramente deve-se verificar se esse nome ja nao existe em nenhum
            //escopo visivel
            if(!pilhaDeTabelas.existeSimbolo(nome))
            {   tabelaDeSimbolosAtual.adicionarSimbolo(nome,tipo, null, null,valor);
            }    
            else 
                out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
            
        }else
        {   //Se no comeco da regra esta presente a palavra chave tipo, isso quer dizer que se trata da declaracao
            //de um registro
            if(ctx.getStart().getText().equals("tipo"))
            {
                //variavel que armazenara o nome do registro
                String nomeDoReg = ctx.IDENT().getText();
                int linhaDoReg = ctx.IDENT().getSymbol().getLine();
                //Antes de adicionar qualquer nome novo na tabela de simbolos, e necessario verificar se esse nome 
                // ja foi declarado em algum escopo visivel
                if(!tabelaDeSimbolosAtual.existeSimbolo(nomeDoReg))
                {
                    //Para a adicao do registro na tabela de simbolos foi criado um novo metodo AdicionarSimboloRegistro
                    //Esse metodo tem o intuito de modularizar essa etapa e evitar repeticao de codigo, ja que e possivel
                    //criar um registro a partir da palavra chave "declare", o que causaria repeticao de codigo nessa etapa
                    //Os parametros passados ao metodo incluem o contexto atual, a tabela de simbolos atual, o nome do registro
                    // e a linha em que esse nome foi declarado, para reportar erros.
                    AdicionarSimboloRegistro(ctx, tabelaDeSimbolosAtual, nomeDoReg, linhaDoReg);
                   
                }else
                    out.println("Linha "+linhaDoReg+": identificador " +nomeDoReg+ " ja declarado anteriormente");
            }   
                else
                {
                    if(ctx.getStart().getText().equals("declare"))
                    {
                       //Para adicionar nomes de variaveis na tabela de simbolos foi criado o metodo AdicionarSimbolos
                       //com intuiro de evitar repeticao de codigo e garantir modularizacao
                       //Os parametros passados sao o contexto atual e a tabela de simbolos atual
                        AdicionarSimbolo(ctx, tabelaDeSimbolosAtual);
                        
                    }
                }
            }
            
        
    }    
    
    
    /**
     *Metodo para adicionar um simbolo em uma tabela do registro e depois adicionar essa tabela, associada ao
     * nome do registro, na tabela de simbolos atual
     */
    public void AdicionarSimboloRegistro(FAZEDORESParser.Declaracao_localContext ctx, TabelaDeSimbolos tabelaDeSimbolosAtual, String nomeDoReg, int LinhaDoReg)
    { 
        String nome;   //nome da variavel
        String tipo; //tipo da variavel
        int linha; //linha da variavel
        //criando uma nova tabela para o registro
        TabelaDeSimbolos tabelaDoRegistro = new TabelaDeSimbolos("registro");
            //Observacao: nao havia caso de teste em que tinha algum tipo nao declarado dentro das declaracoes do 
            //registro. Essa verificacao nao foi adicionada nesse metodo, mas deveria ter sido =[.
            if(ctx.tipo()!=null)       
            {      nome = ctx.tipo().registro().variavel().IDENT().getText(); //nome da variavel
                   linha = ctx.tipo().registro().variavel().IDENT().getSymbol().getLine();  //linha da variavel
                   if(ctx.tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                   {
                       //verificacao para determinar o tipo, ja que ele pode ser um novo tipo (definido em um registro)
                       // ou um tipo pre definido pela gramatica
                       tipo = ctx.tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT().getText();
                   }else
                   {
                       //nesse caso, recupera o tipo pre estabelecido da gramatica. 
                       tipo = ctx.tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                   }
                   
                   //antes de adicionar a variavel a tabela de simbolos do registro, e necessario verificar se ja nao existe nenhum
                   //nome igual nessa tabela
                   if(!tabelaDoRegistro.existeSimbolo(nome))
                   {
                       tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                   }else
                   {
                       out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                   }
                   
                   //Realizando o mesmo processo para as outras variaveis que vem do mais_var, que possuem
                   //o mesmo tipo declarado da variavel anterior
                   for(int i = 0; i<ctx.tipo().registro().variavel().mais_var().IDENT().size(); i++)
                   {
                       nome = ctx.tipo().registro().variavel().mais_var().IDENT(i).getText();
                       linha = ctx.tipo().registro().variavel().mais_var().IDENT(i).getSymbol().getLine();
                       if(!tabelaDoRegistro.existeSimbolo(nome))
                       {
                           tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                       }else
                       {
                           out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                       }
                   }
                   
                   //O mesmo processo realizado anteriormente para a regra variavel, deve ser realizado para a 
                   // regra mais_variaveis, que permite a inclusao de outras variaveis de diferentes tipo no mesmo
                   // declare
                   
                   for(int i = 0; i < ctx.tipo().registro().mais_variaveis().variavel().size(); i++) 
                   {
                        nome = ctx.tipo().registro().mais_variaveis().variavel(i).IDENT().getText();
                        linha = ctx.tipo().registro().mais_variaveis().variavel(i).IDENT().getSymbol().getLine();
                        if(ctx.tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                        {
                            tipo = ctx.tipo().registro().mais_variaveis().variavel(i).tipo().tipo_estendido().tipo_basico_ident().IDENT().getText();
                        }else
                        {
                            tipo = ctx.tipo().registro().mais_variaveis().variavel(i).tipo().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                        }
                   
                        if(!tabelaDoRegistro.existeSimbolo(nome))
                        {
                            tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                        }else
                        {
                            out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                        }
                   
                        for(int j = 0; j<ctx.tipo().registro().mais_variaveis().variavel(i).mais_var().IDENT().size(); j++)
                        {
                            nome = ctx.tipo().registro().mais_variaveis().variavel(i).mais_var().IDENT(i).getText();
                            if(!tabelaDoRegistro.existeSimbolo(nome))
                            {
                                tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                            }else
                            {
                                out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                            }
                        }
                   }
            }else{
                if(ctx.variavel().tipo()!=null)
                {
                   nome = ctx.variavel().tipo().registro().variavel().IDENT().getText();
                   linha = ctx.variavel().tipo().registro().variavel().IDENT().getSymbol().getLine();
                   if(ctx.variavel().tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                   {
                       tipo = ctx.variavel().tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT().getText();
                   }else
                   {
                       tipo = ctx.variavel().tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                   }
                   
                   if(!tabelaDoRegistro.existeSimbolo(nome))
                   {
                       tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                   }else
                   {
                       out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                   }
                   
                   for(int i = 0; i<ctx.variavel().tipo().registro().variavel().mais_var().IDENT().size(); i++)
                   {
                       nome = ctx.variavel().tipo().registro().variavel().mais_var().IDENT(i).getText();
                       if(!tabelaDoRegistro.existeSimbolo(nome))
                       {
                           tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                       }else
                       {
                           out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                       }
                   }
                   
                   for(int i = 0; i < ctx.variavel().tipo().registro().mais_variaveis().variavel().size(); i++) 
                   {
                        nome = ctx.variavel().tipo().registro().mais_variaveis().variavel(i).IDENT().getText();
                        linha = ctx.variavel().tipo().registro().mais_variaveis().variavel(i).IDENT().getSymbol().getLine();
                        if(ctx.variavel().tipo().registro().variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                        {
                            tipo = ctx.variavel().tipo().registro().mais_variaveis().variavel(i).tipo().tipo_estendido().tipo_basico_ident().IDENT().getText();
                        }else
                        {
                            tipo = ctx.variavel().tipo().registro().mais_variaveis().variavel(i).tipo().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                        }
                   
                        if(!tabelaDoRegistro.existeSimbolo(nome))
                        {
                            tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                        }else
                        {
                            out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                        }
                   
                        for(int j = 0; j<ctx.variavel().tipo().registro().mais_variaveis().variavel(i).mais_var().IDENT().size(); j++)
                        {
                            nome = ctx.variavel().tipo().registro().mais_variaveis().variavel(i).mais_var().IDENT(i).getText();
                            if(!tabelaDoRegistro.existeSimbolo(nome))
                            {
                                tabelaDoRegistro.adicionarSimbolo(nome, tipo, null, null);
                            }
                        }
                }
            } 
        }     
        
        //Apos adicionar todas as variaveis a tabela de simbolos do registro, podemos adicionar essa tabela
        //ao escopo do contexto atual. O nome vai ser o nome do registrador e o tipo tambem, para permitir pesquisas
        // e recuperacao desse tipo
        tabelaDeSimbolosAtual.adicionarSimbolo(nomeDoReg, nomeDoReg, null, tabelaDoRegistro);
                
    }
    
    
    /**
     * Esse metodo tem o intuito de adicionar uma nova variavel a tabela de simbolos, a partir da palavra chave
     * declare.
     */
    public void AdicionarSimbolo(FAZEDORESParser.Declaracao_localContext ctx, TabelaDeSimbolos tabelaDeSimbolosAtual)
    {
        String nome;
        String tipo;
        int linha;
        
            //Verificacao inicial para garantir que esses comandos nao sejam executados quando entrar na regra registro
            //so por precaucao
            if(ctx.variavel().tipo().registro()==null)       
            {      nome = ctx.variavel().IDENT().getText();
                   linha = ctx.variavel().IDENT().getSymbol().getLine();
                   
                   //primeira verificacao para ver se o tipo ja foi declarado anteriormente or meio de um registro
                   // se nao foi, um  erro deve ser reportado
                   if(ctx.variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                   {
                       tipo = ctx.variavel().tipo().tipo_estendido().tipo_basico_ident().IDENT().getText();

                       if(!pilhaDeTabelas.existeSimbolo(tipo))
                       {
                           out.println("Linha "+linha+ ": tipo "+tipo+" nao declarado");
                       }
                   }else
                   {
                       tipo = ctx.variavel().tipo().tipo_estendido().tipo_basico_ident().tipo_basico().getText();

                   }
                   
                   //verificando se na tabela de simbolos atual existe alguma variavel com nome igual a nome
                   if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                   {
                       tabelaDeSimbolosAtual.adicionarSimbolo(nome, tipo, null, null);
                   }else
                   {
                       out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                   }
                   
                   //realizando os mesmos passsos para as variaveis que seguem a declaracao de mesmo tipo
                   //A regra mais_var permite que sejam adicionadas mais variaveis daquele mesmo tipo
                   for(int i = 0; i<ctx.variavel().mais_var().IDENT().size(); i++)
                   {
                       nome = ctx.variavel().mais_var().IDENT(i).getText();
                       linha = ctx.variavel().mais_var().IDENT(i).getSymbol().getLine();
                       if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                       {
                           tabelaDeSimbolosAtual.adicionarSimbolo(nome, tipo, null, null);
                       }else
                       {
                           out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                       }
                   }
            }else{
                //Se a verificacao da regra registro nao resultou em null, isso quer dizer que dentro de declare
                // um novo registro foi declarado. O tratamento entao deve ser distinto
                nome = ctx.variavel().IDENT().getText();
                linha = ctx.variavel().IDENT().getSymbol().getLine();
                //verificando se nao existe nenhum nome com aquele nome ja declarado
                if(!pilhaDeTabelas.existeSimbolo(nome))
                {
                    //Nesse ponto, pode se chamar o procedimento para adicionar variaveis a tabela de simbolos do
                    //procedimento, passando a tabela de simbolos do escopo atual, o contexto atual, o nome do regitro
                    //e sua linha. E bom lembrar que a tabela de simbolos do registro e criada dentro desse procedimento
                    AdicionarSimboloRegistro(ctx, tabelaDeSimbolosAtual, nome, linha);
                }
                
                //E possivel ter mais nomes associados a uma mesma declaracao de um registro quando se vem da regra
                //declare. Entao, para cada nome que tiver na repeticao dessa regra, a mesma definicao do registro
                //deve estar associada com cada um desses nomes diferentes, entao para cada um se chama AdicionarSimboloRegistro
                for(int i = 0; i<ctx.variavel().mais_var().IDENT().size(); i++)
                   {
                       nome = ctx.variavel().mais_var().IDENT(i).getText();
                       if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                       {
                           AdicionarSimboloRegistro(ctx, tabelaDeSimbolosAtual, nome, linha);
                       }else
                       {
                           out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                       }
                   }
            }       
    }

    
    /**
     * Metodo sobrescrito do listener para representar as verificacoes que devem ser feitas toda vez que se entrar
     * na regra cmd
     */ 
     @Override
    public void enterCmd(FAZEDORESParser.CmdContext ctx)
    {   
        TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo();
        int linha;
        String nome;
        String tipo;
        
        //Verificando o comeco do contexto atual para determinar o comportamento correto.
        if(ctx.getStart().getText().equals("leia"))
        {
            //Se comecar com leia, significa que existem identificadores dentro desse comando. Deve-se verificar
            //Se eles ja foram declarados anteriormente. Para isso foi criado o procedimento VerificaSimbolo.
            VerificaSimbolo(ctx);
        }else
        {
            //Quando expressao e IDENT sao diferentes de null ao mesmo tempo, temos uma atribuicao para um ponteiro
            if(ctx.expressao()!=null && ctx.IDENT()!=null)
            {
                //nome da variavel que recebe a atribuicao
                nome = ctx.IDENT().getText();
                //recuperacao do tipo da variavel armazenado em alguma tabela de simbolos
                tipo = pilhaDeTabelas.getTipo(nome);
                if(tipo == null)
                {
                    tipo = "tipo_indefinido";
                }
                nome = "^" + nome; //ajustando o nome, para representar o ponteiro
                linha = ctx.IDENT().getSymbol().getLine();
                
                //No caso de o nome ser um nome composto, recuperar a outra parte do nome
                if(ctx.outros_ident().identificador()!=null)
                {
                    TabelaDeSimbolos tabelaDoRegistro = pilhaDeTabelas.getSubtabela(tipo);
                    String Outronome = ctx.outros_ident().identificador().IDENT().getText();
                    tipo = tabelaDoRegistro.getTipo(Outronome);
                    nome = nome + "." + Outronome;
                    
                }
                //Agora, e necessario verificar se a atribuicao foi feita para tipos compativeis, para isso,
                //foi criado um metodo que recebe como parametro o contexto da expressao (para recuperar seu tipo)
                // o nome, a linha e o tipo da cabeca da atribuicao
                VerificaAtribuicao(ctx.expressao(), nome, linha, tipo);
                
                
            }else
            {
              //ainda e possivel ter atribuicao por essa regra    
              if(ctx.chamada_atribuicao()!=null)  
              {  if(ctx.chamada_atribuicao().expressao()!=null)
                {
                    //recuperando o nome da variavel que recebe a atribuicao
                    nome = ctx.IDENT().getText();
                    //recuperando o seu tipo da tabela de simbolos
                    tipo = pilhaDeTabelas.getTipo(nome);
                    if(tipo == null)
                    {
                        //se nao ha registro para o tipo, ou para o nome na tabela de simbolos, o tipo
                        //passa a ser indefinido
                        tipo = "tipo_indefinido";
                    }
                    linha = ctx.IDENT().getSymbol().getLine();
                    //verificacao para saber se o identificador ja foi declarado anteriormente
                    if(!pilhaDeTabelas.existeSimbolo(nome))
                    {
                        out.println("Linha "+linha+": identificador "+nome+" nao declarado");
                    }
                    //se a regra outros_ident() for diferente de null, temos que que o nome eh composto. O nome entao
                    //deve ser escrito pela combinacao identificador . outros_ident e o tipo da variavel eh o tipo
                    // do nome mais interno, entao, a tabela do registro do tipo da primeria variavel deve ser recuperada
                    //para verificar o tipo do nome mais interno.
                    if(ctx.chamada_atribuicao().outros_ident().identificador()!=null)
                    {
                        TabelaDeSimbolos tabelaDoRegistro = pilhaDeTabelas.getSubtabela(tipo);
                        String Outronome = ctx.chamada_atribuicao().outros_ident().identificador().IDENT().getText();
                        tipo = tabelaDoRegistro.getTipo(Outronome);
                        nome = nome + "." + Outronome;
                    
                    }
                    //Esse trecho de codigo e para os casos de existirem vetores, ou ate mesmo variaveis com maior dimensao,
                    //para recuperar a expressao interior dessa dimensao e reescrever o nome da maneira correta
                    DimensaoContext dCtx = ctx.chamada_atribuicao().dimensao();
                    if(dCtx != null) {
                        FAZEDORESParser.Exp_aritmeticaContext eCtx = dCtx.exp_aritmetica(0);
                        if(eCtx != null) {
                            String txt = eCtx.getText();
                            nome = nome +"["+txt+"]";
                        }
                   }
                
                   // Com o contexto da expressao, e o tipo da variavel que recebe a atribuicao, e possivel realizar a 
                   // verificacao dessa atribuicao, se os tipos sao compativeis com o uso do metodo VerificaAtribuicao.
                   //Tambem e passado como parametro o nome da variavel e a linha para poder reportar erros
                    VerificaAtribuicao(ctx.chamada_atribuicao().expressao(), nome, linha, tipo);
                }
            }else
              {
                  //Um comando pode ser retorne. Eh necessario verificar se esse retorne foi colocado no escopo correto,
                  // pois se houver algum retorne em algoritmo e procedimento, deve ser reportado um erro. Quando for escopo
                  // de funcao, esse retorne esta no escopo correto, o atributo EhFuncao, que funciona como uma flag, recebe 
                  //o valor 1 quando uma funcao e declarada. Se nao, ele continua com 0. A verificacao feita, entao, e se o atributo
                  //e igual a 0, se for, o comando retorne esta em um escopo nao permitido.
                  if(ctx.getStart().getText().equals("retorne"))
                  {
                      linha = ctx.getStop().getLine();
                      if(EhFuncao == 0)
                      {
                          out.println("Linha "+linha+": comando retorne nao permitido nesse escopo");
                      }
                      
                  }
              }
          }    
        }
    }
    
    /**
     * O metodo VerificaAtribuicao e usado para verificar compatibilidade de tipos quando ocorre uma atribuicao.
     * Para isso, deve se verificar se o tipo da cabeca da atribuicao e compativel com o que esta sendo atribuido 
     * a ela. Recebe como parametros o contexto da expressao a ser analisada, o nome da variavel que esta recebendo
     * a atribuicao (para reportar erros), a linha, e o tipo da variavel que esta recebendo a atribuicao para realizar
     * a comparacao.
     */
    public void VerificaAtribuicao(FAZEDORESParser.ExpressaoContext ctx, String nome, int linha, String Tipo)
    {
        //Para realizar a verificacao de tipos, foi criada uma classe que retorna o tipo de uma expressao (mais explicacoes na classe)
        VerificadorDeTipos verificador = new VerificadorDeTipos();
    
        //O tipo da expressao e retornado pelo metodo verificatipo (que recebe o contexto da expressao como parametro, 
        //na verdade, esse metodo e polimorfico, e depende do contexto que e passado para determinar seu comportamento.)
        // da classe VerificadorDeTipos
        String TipoAtribuido = verificador.verificaTipo(ctx);
        
        //A primeira verificacao feita entre os tipos da variavel que esta recebendo a atribuicao e o tipo retornado
        //da expressao, eh se esses tipos sao iguais. Se eles foram, nenhum teste mais necessita ser feito, a atribuicao
        //e valida. Se nao forem, a atribuicao ainda pode ser valida se os tipos forem compativeis, entao e necessario 
        //continuar os testes.
        if(!Tipo.equals(TipoAtribuido))
        {
            //se o tipo da variavel que recebe a atribuicao nao foi recuperado da tabela, ele e indefinido, e deve resultar
            //em uma atribuicao invalida
            if(!Tipo.equals("tipo_indefinido"))
            {
                //Se o tipo retornado pelo verificador de tipos foi tipo_indefinido, isso significa que em algum ponto da analise
                //dentro da expressao, os tipos interiores dessa expressao sao incompativeis, a expressao em si nao tem tipo valido.
                //Se algum dos tipos for literal (lembrando que os dois nao podem ser iguais ao mesmo tempo nesse ponto), entao a
                //atribuicao naoe equivalente.
                if(Tipo.equals("literal") || TipoAtribuido.equals("literal") || TipoAtribuido.equals("tipo_invalido"))
                {
                    out.println("Linha "+linha+": atribuicao nao compativel para "+nome);
                }
            }
            
        }
        
    }
    
    /**
     * O metodo verifica simbolo tem intencao de realizar a verificacao de tipos para identificadores. O metodo
     * foi criado com intuito de modularizar esses comandos. Ele e chamado quando existe um comando leia, entao
     * o contexto passado e o contexto de comando.
     */
    public void VerificaSimbolo(FAZEDORESParser.CmdContext ctx)
    {
        //recuperando o nome o identificador
        String nome = ctx.identificador().IDENT().getText();
        int linha = ctx.identificador().IDENT().getSymbol().getLine();
        String tipo = pilhaDeTabelas.getTipo(nome);  //recuperando seu tipo
        String nomeOutrosIdent = null;
        
        //se for um nome composto, e necessario recuperar o restante do nome, e mais importante ainda,
        //A partir do tipo (lembrando que o nome do registro e salvo no campo tipo), procura-se a tabela
        //do registro na pilha de tabelas e a recupera, pois o nome retornado por outros_ident() deve ser procurado
        //na tabela de simbolos do registro equivalente
        if(ctx.outros_ident()!=null)
        {
            if(ctx.outros_ident().identificador().IDENT()!=null)
            {   nomeOutrosIdent = ctx.outros_ident().identificador().IDENT().getText();
                //obtendo a subtabela
                TabelaDeSimbolos tabReg = pilhaDeTabelas.getSubtabela(tipo);
                //se o nome nao existir na subtabela, reportar o erro
                if(!tabReg.existeSimbolo(nomeOutrosIdent))
                {
                    out.println("Linha "+linha+": identificador "+nome+"."+nomeOutrosIdent+" nao declarado");  
                }
            }
        }
        
        //O mesmo vale para o nome principal, se ele nao foi declarado, declarar erro. Importante notar que a
        //declaracao de erros e feita tanto com o nome principal, quanto com o subnome, mesmo que apenas um dos
        //dois nao tenha sido declarado.
        if(!pilhaDeTabelas.existeSimbolo(nome))
        {
            if(nomeOutrosIdent!=null)
            {
                nome = nome + "." + nomeOutrosIdent;
                nomeOutrosIdent = null;
            }
            out.println("Linha "+linha+": identificador "+nome+" nao declarado");            
        }
        
        //Para poder garantir a repeticao de identificadores dentro desses comandos, existe a regra mais_ident().
        //A mesma verificacao que foi feita pro identificador anteriormente, deve ser feita para cada um dos identificadores
        //retornados por essa regra.
        for(int i = 0; i < ctx.mais_ident().identificador().size(); i++)
        {
            nome = ctx.mais_ident().identificador(i).IDENT().getText();
            linha = ctx.mais_ident().identificador(i).IDENT().getSymbol().getLine();
            if(ctx.mais_ident().identificador(i).outros_ident()!=null)
            {
                if(ctx.mais_ident().identificador(i).outros_ident().identificador()!=null)
                {nomeOutrosIdent = ctx.mais_ident().identificador(i).outros_ident().identificador().IDENT().getText();
                TabelaDeSimbolos tabReg = pilhaDeTabelas.getSubtabela(tipo);
                if(!tabReg.existeSimbolo(nomeOutrosIdent))
                {
                    out.println("Linha "+linha+": identificador "+nome+"."+nomeOutrosIdent+" nao declarado");  
                }
                }
            }
        
            if(!pilhaDeTabelas.existeSimbolo(nome))
            {
                if(nomeOutrosIdent!=null)
                {
                    nome = nome + "." + nomeOutrosIdent;
                    nomeOutrosIdent = null;
                }
                out.println("Linha "+linha+": identificador "+nome+" nao declarado");            
            }
            
        }
        
    }
    
    /**
     * Metodo sobrescrito do listener para adicionar as verificacoes necessarias dessa regra
     */
    @Override
    public void enterDeclaracao_global(FAZEDORESParser.Declaracao_globalContext ctx)
    {
        String nome = ctx.IDENT().getText();  //representa ou o nome do procedimento, ou o nome da funcao
        TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo(); //tabela de simbolos do escopo que sera adicionado a subrotina
        List<String> listaNomePar = new ArrayList<>();  //lista para armazenar os nomes dos parametros
        List<String> listaTipoPar = new ArrayList<>();  //lista para armazenar os tipos dos parametros
        //Se for um procedimento...
        if (ctx.getStart().getText().equals("procedimento"))
        {
            //Primeiramente, verificar se o nome do procedimento ja nao foi declarado anteriormente
            if(!pilhaDeTabelas.existeSimbolo(nome))
            {
                //se esse procedimento tem parametros
                if(ctx.parametros_opcional()!=null)
                {   
                    //tratar a verificacao dos parametros em um metodo diferente, passando como parametro o contexto
                    //atual e as duas listas que vao salvar essas informacoes
                    AdicionarTiposParametros(ctx,listaNomePar,listaTipoPar);
                }
                
                //Adiciona a tabela de simbolos atual, ou seja, ao escopo atual, a lista de tipos dos parametros
                //associada ao nome do procedimento. Importante notar que o tipo do procedimento e null
                tabelaDeSimbolosAtual.adicionarSimbolo(nome, null, listaTipoPar, null);
                //Cria a nova tabela, a do escopo do procedimento
                TabelaDeSimbolos tabelaDeSimbolosProcedimento = new TabelaDeSimbolos("procedimento"+nome);
                //Todos  os parametros que foram declarados sao adicionados a essa tabela, pois eles sao visiveis nesse
                //escopo
                for(int i = 0; i < listaNomePar.size(); i++)
                {
                    if(!tabelaDeSimbolosProcedimento.existeSimbolo(listaNomePar.get(i)))
                    {
                        tabelaDeSimbolosProcedimento.adicionarSimbolo(listaNomePar.get(i), listaTipoPar.get(i), null, null);
                    }
                }
                //Por fim, a pilha do procedimento e empilhada, ou seja, passa a ser o escopo atual
                pilhaDeTabelas.empilhar(tabelaDeSimbolosProcedimento);  
                
            }else
            {
                out.println("identificador "+nome+" ja declarado anteriormente");
            }
        }else{
            if(ctx.getStart().getText().equals("funcao"))
            {   //atributo que serve como flag para indicar que se trata de uma funcao. Ele sera usado em cmd
                //para verificar se o comando retorne esta no escopo correto
                EhFuncao = 1;
                //A mesma logica de parametros de procedimento e usada aqui, com a mesmo metodo inclusive
                if(!pilhaDeTabelas.existeSimbolo(nome))
                {
                    if(ctx.parametros_opcional()!=null)
                    {   
                        AdicionarTiposParametros(ctx,listaNomePar,listaTipoPar);
                    }
                    String TipoFuncao;
                    //Funcao tem um tipo associado, esse tipo deve ser recuperado principalmente para tratamento
                    //dos tipos de expressoes
                    if(ctx.tipo_estendido().tipo_basico_ident().IDENT()!=null)
                        TipoFuncao = ctx.tipo_estendido().tipo_basico_ident().IDENT().getText();
                    else
                        TipoFuncao = ctx.tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                    
                    tabelaDeSimbolosAtual.adicionarSimbolo(nome, TipoFuncao, listaTipoPar, null);
                    TabelaDeSimbolos tabelaDeSimbolosFuncao = new TabelaDeSimbolos("funcao"+nome);
                    
		    //Adicionando a lista de parametros que foram recuperados anteriormente ao escopo da funcao, pois esses
		    //nomes tambem sao visiveis dentro do escopo das funcoes	
                    for(int i = 0; i < listaNomePar.size(); i++)
                    {
                        if(!tabelaDeSimbolosFuncao.existeSimbolo(listaNomePar.get(i)))
                        {
                            tabelaDeSimbolosFuncao.adicionarSimbolo(listaNomePar.get(i), listaTipoPar.get(i), null, null, null);
                        }
                    }
                    pilhaDeTabelas.empilhar(tabelaDeSimbolosFuncao);  
                
                }else
                {
                    out.println("identificador "+nome+" ja declarado anteriormente");
                }
            }
        }
        
            
    }
    
    /**
     * Quando sao da regra declaracao_global, tambem esta saindo do escopo da funcao ou do procedimento, entao e
     * necessario desempilhar. EhFuncao passa a ter o valor 0, para que nao atrapalhe em futuros testes.
     */ 
    @Override
    public void exitDeclaracao_global(FAZEDORESParser.Declaracao_globalContext ctx) {
        pilhaDeTabelas.desempilhar();
        EhFuncao = 0;
    }
    

    /**
    * Metodo sobrescrito do listener que e percorrido pelo parser tree walker quando existe uma chamada a uma subrotina, para verficar
    * se os parametros passados possuem tipos compativeis com os parametros declarados
    */    
    @Override
    public void enterChamada_partes(FAZEDORESParser.Chamada_partesContext ctx)
    {
        if(ctx.getStart().getText().equals("("))
        {   //a verificacao dos parametros deve ser feita se a regra iniciar-se com "("
            List<String> ParametrosFormais = new ArrayList<>();
            String nomeSubRotina = ctx.getParent().getStart().getText();
            int Linha = ctx.getParent().getStart().getLine();
            ParametrosFormais = pilhaDeTabelas.getListaPar(nomeSubRotina);
            //Declarando uma lista para armazenar cada um dos parametros passados
            List<String> ParametrosReais = new ArrayList<>();
            ParametrosReais.add(VerificadorDeTipos.verificaTipo(ctx.expressao()));
	    //Obtendo o tipo de cada um dos nomes passados como parametro
            for(FAZEDORESParser.ExpressaoContext eCtx : ctx.mais_expressao().expressao()) {
                ParametrosReais.add(VerificadorDeTipos.verificaTipo(eCtx));
            }

	    //realizando as comparacoes para determinar se a chamada e equivalente a declaracao, se nao tiverem a mesma quantidade
            //isso ja representa um erro            
            if(ParametrosFormais.size() != ParametrosReais.size())
            {
                 out.println("Linha "+Linha+": incompatibilidade de parametros na chamada de "+ nomeSubRotina);
            }else
            { //Se a quantidade for a mesma, e necessario verificar, parametro por parametro se eles sao de tipos iguais, se nao forem, isso constitui um erro
                for(int i=0; i< ParametrosReais.size(); i++)
                 {
                     String tipo1 = ParametrosReais.get(i);
                     String tipo2 = ParametrosFormais.get(i);
                
                    if(!tipo1.equals(tipo2))
                    {
                       out.println("Linha "+Linha+": incompatibilidade de parametros na chamada de "+ nomeSubRotina);
                    }
                }
            }
        }
    }
    
    //Metodo criado para modularizar a adicao de parametros de uma subrotina, recebendo como parametro uma lista de nomes (para adicionar
    // os identificadores ao escopo do procedimento, juntamente com seu tipo) e uma lista de tipos, parra ser armazenada associada ao
    //nome da sunrotina. Assim  como em identificadores e variaveis, sao feitas as mesmas verificacoes e, se existe alguma regra para
    // repeticao, entao essas etapas sao feitas em todas as regras repetidas.
    public void AdicionarTiposParametros(FAZEDORESParser.Declaracao_globalContext ctx, List<String> ListaNomePar, List<String> ListaTipoPar)
    {
        String nome;
        String tipo;
        int linha;
        TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo();
        
        if(ctx.parametros_opcional().parametro()!=null)
        {
            
        
        
        if(ctx.parametros_opcional().parametro().identificador()!=null)
        {
            
            nome = ctx.parametros_opcional().parametro().identificador().IDENT().getText();
            linha = ctx.parametros_opcional().parametro().identificador().IDENT().getSymbol().getLine();
            if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
            {
                ListaNomePar.add(nome);
            }else{
            
                out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
            }
        
            if(ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().IDENT()!=null)
            {
                tipo = ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().IDENT().getText();
            }else
            {
                tipo = ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
            }
        
             ListaTipoPar.add(tipo);
        
            for(int i = 0; i<ctx.parametros_opcional().parametro().mais_ident().identificador().size(); i++)
            {
                nome = ctx.parametros_opcional().parametro().mais_ident().identificador(i).IDENT().getText();
                linha = ctx.parametros_opcional().parametro().mais_ident().identificador(i).IDENT().getSymbol().getLine();
                if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                {
                    ListaNomePar.add(nome);
                }else{
            
                    out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                }
                ListaTipoPar.add(tipo);
            }
        
            if(ctx.parametros_opcional().parametro().mais_parametros().parametro()!=null)
            {
                nome = ctx.parametros_opcional().parametro().mais_parametros().parametro().identificador().IDENT().getText();
                linha = ctx.parametros_opcional().parametro().mais_parametros().parametro().identificador().IDENT().getSymbol().getLine();
                if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                {
                    ListaNomePar.add(nome);
                }else{
            
                    out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                }
        
                if(ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().IDENT()!=null)
                {
                    tipo = ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().IDENT().getText();
                }else
                {
                    tipo = ctx.parametros_opcional().parametro().tipo_estendido().tipo_basico_ident().tipo_basico().getText();
                }
        
                ListaTipoPar.add(tipo);
        
            for(int i = 0; i < ctx.parametros_opcional().parametro().mais_parametros().parametro().mais_ident().identificador().size(); i++)
            {
                nome = ctx.parametros_opcional().parametro().mais_parametros().parametro().mais_ident().identificador(i).IDENT().getText();
                linha = ctx.parametros_opcional().parametro().mais_parametros().parametro().mais_ident().identificador(i).IDENT().getSymbol().getLine();
                if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                {
                    ListaNomePar.add(nome);
                }else{
            
                    out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
                }
                ListaTipoPar.add(tipo);
            }
            
            
            }
        }   
    }  
    }
    
    /**
     * Metodo sobrescrito do listener para tratar os identificadores, verificar se eles foram declarados anteriormente.
     */
     @Override
    public void enterParcela_unario(FAZEDORESParser.Parcela_unarioContext ctx)
    {
        String tipo = null;
        VerificadorDeTipos verificador = new VerificadorDeTipos();
        if(ctx.IDENT()!=null)
        {
            //Obtendo o nome do identificador, e verificando se ele ja foi declarado anteriormente
            String nome = ctx.IDENT().getText();
            int linha = ctx.IDENT().getSymbol().getLine();
            tipo = pilhaDeTabelas.getTipo(nome);
            if(!pilhaDeTabelas.existeSimbolo(nome))
            {
                if(ctx.chamada_partes()!=null)
                {
                    if (ctx.chamada_partes().outros_ident().identificador() != null)
                    {
                        nome = nome + '.' + ctx.chamada_partes().outros_ident().identificador().IDENT().getText();
                    }
                }
                
                out.println("Linha "+linha+": identificador "+nome+" nao declarado");
            }
        }
            
    }
        
    /**
    * Metodo sobrescrito do listener, para poder fazer as mesmas verificacoes feitas no enterParcela_unario, pois nessa regra tambem e possivel
    * ter identificadores.
    */
    @Override
    public void enterParcela_nao_unario(FAZEDORESParser.Parcela_nao_unarioContext ctx)
    {
        String tipo = null;
        if(ctx.IDENT()!=null)
        {
            String nome = ctx.IDENT().getText();
            int linha = ctx.IDENT().getSymbol().getLine();
            tipo = pilhaDeTabelas.getTipo(nome);
            if(!pilhaDeTabelas.existeSimbolo(nome))
            {
                if (ctx.outros_ident().identificador()!= null)
                {   
                    nome = nome + '.' + ctx.outros_ident().identificador().IDENT().getText();
                }
                
                out.println("Linha "+linha+": identificador "+nome+" nao declarado");
            }
        }
    }
    
    
  
}
