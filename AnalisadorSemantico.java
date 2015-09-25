/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package trabalho1;

import java.util.ArrayList;
import java.util.List;
import org.antlr.runtime.RecognitionException;
import trabalho1.LAParser.Declaracao_localContext;

/**
 *
 * @author gabriela
 */
public class AnalisadorSemantico extends LABaseListener {

    Saida out;

    PilhaDeTabelas pilhaDeTabelas = new PilhaDeTabelas();
    
    public AnalisadorSemantico(Saida out) {
        this.out = out;
    }

    @Override
    public void enterPrograma(LAParser.ProgramaContext ctx) {
        
        pilhaDeTabelas.empilhar(new TabelaDeSimbolos("global"));
        
        
    }
    
 
    
    @Override
    public void enterDeclaracao_local(Declaracao_localContext ctx) {
           
        if(ctx.variavel() != null) {
            TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo();
            //tabelaDeSimbolosAtual.adicionarSimbolo(null, "literal", null);
            
            for (int i=0; i<ctx.variavel().variaveis.size(); i++)  
            {
                int linha;
                String nomeVar = ctx.variavel().variaveis.get(i);
                String tipoVar = ctx.variavel().tipo_var;
                if(i==0)
                    linha = ctx.variavel().IDENT().getSymbol().getLine();
                else
                    linha = ctx.variavel().mais_var().IDENT(i - 1).getSymbol().getLine();
                
                if(tipoVar == "literal" || tipoVar == "inteiro" || tipoVar == "real" || tipoVar == "logico")
                {
                    if(!tabelaDeSimbolosAtual.existeSimbolo(nomeVar))
                    {   tabelaDeSimbolosAtual.adicionarSimbolo(nomeVar,tipoVar, null);
                            System.out.println("Var adicionada "+nomeVar+" "+tipoVar+" linha: "+linha);
                    }    
                    else //ERRO 1
                      out.println("Linha "+linha+": identificador " +nomeVar+ " ja declarado anteriormente");
                }
                else
                {
                     if(!tabelaDeSimbolosAtual.existeSimbolo(tipoVar))
                     {
                         out.println("Linha "+linha +  ": tipo "+tipoVar+" nao declarado");
                         /*Duvida aqui, pq essa variavel nao podia ser adicionada, mas no teste do professor ela foi */
                         tabelaDeSimbolosAtual.adicionarSimbolo(nomeVar, tipoVar, null);
                     }else
                     {
                         if(!tabelaDeSimbolosAtual.existeSimbolo(nomeVar))
                        	tabelaDeSimbolosAtual.adicionarSimbolo(nomeVar,tipoVar, null);	
                         else //ERRO 1
                           out.println("Linha "+linha+": identificador "+nomeVar+" ja declarado anteriormente");
                     }
                }
                
            }                
        }else
        {
            if(ctx.getStart().getText().equals("constante"))
            {
                String nome = ctx.IDENT().getText();
                int linha = ctx.IDENT().getSymbol().getLine();
                String tipo = ctx.tipo_basico().tipo_var;
                
                TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo();
                
                if(!tabelaDeSimbolosAtual.existeSimbolo(nome))
                    {   tabelaDeSimbolosAtual.adicionarSimbolo(nome,tipo, null);
                            System.out.println("Var adicionada "+nome+" "+tipo+" linha: "+linha);
                    }    
                    else //ERRO 1
                      out.println("Linha "+linha+": identificador " +nome+ " ja declarado anteriormente");
            }else{
                 if(ctx.getStart().getText().equals("tipo"))
                 {
                     String novoTipo = ctx.IDENT().getText();
                     TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo();
                     tabelaDeSimbolosAtual.adicionarSimbolo(novoTipo, "tipo", null);
                 }
            }
        }
    }
    
    @Override
    public void enterCmd(LAParser.CmdContext ctx)
    {   
        TabelaDeSimbolos tabelaDeSimbolosAtual = pilhaDeTabelas.topo();
        int linha;
        String nomeVar;
        if(ctx.getStart().getText().equals("leia"))
        {
            nomeVar = ctx.identificador().array_id.get(0);
            linha = ctx.identificador().IDENT().getSymbol().getLine();
            
            if(!tabelaDeSimbolosAtual.existeSimbolo(nomeVar))
            {
                out.println("Linha "+linha+": identificador "+nomeVar+" nao declarado");
            }
            
            for(int i=0; i < ctx.mais_ident().array_id.size(); i++)
            {
                
                nomeVar = ctx.mais_ident().array_id.get(i);
                System.out.println("Parametro passado, nome: "+nomeVar);
                linha = ctx.identificador().IDENT().getSymbol().getLine();
                if(!tabelaDeSimbolosAtual.existeSimbolo(nomeVar))
                {
                    out.println("Linha "+linha+": identificador "+nomeVar+" nao declarado");
                }
            }    
            
            
        }else
        {
            if(ctx.getStart().getText().equals("escreva"))
            {
                nomeVar = ctx.expressao().nome_par.get(0);
                linha = ctx.expressao().linha.get(0);
                if(nomeVar!=null)
                {
                    if(!tabelaDeSimbolosAtual.existeSimbolo(nomeVar))
                    {
                        out.println("Linha "+linha+": identificador "+nomeVar+" nao declarado");
                    }
                }
            
                for(int i=0; i < ctx.mais_expressao().nome_par.size(); i++)
                {
                
                    nomeVar = ctx.mais_expressao().nome_par.get(i);
                    System.out.println("Parametro passado, nome: "+nomeVar);
                    linha = ctx.mais_expressao().linha.get(i);
                    if(nomeVar!=null)
                    {
                        if(!tabelaDeSimbolosAtual.existeSimbolo(nomeVar))
                        {
                            out.println("Linha "+linha+": identificador "+nomeVar+" nao declarado");
                        }
                    }
                    
                }    
            
            
            }
        }
    }
    
    @Override
    public void enterDeclaracao_global(LAParser.Declaracao_globalContext ctx)
    {
        String nome = ctx.IDENT().getText();
        
        if (ctx.getStart().getText().equals("procedimento"))
        {
            
            List<String> listaPar = new ArrayList<String>();
            TabelaDeSimbolos tabelaDeSimbolosGlobal = pilhaDeTabelas.topo();
                
            if(!tabelaDeSimbolosGlobal.existeSimbolo(nome))
            {
                TabelaDeSimbolos tabelaDeSimbolosProcedimento = new TabelaDeSimbolos("procedimento "+nome);
                
                for (int i=0; i<ctx.parametros_opcional().parametros.size(); i++)      
                {
                    String nomePar = ctx.parametros_opcional().parametros.get(i);
                    String tipoPar = ctx.parametros_opcional().tipo_parametros.get(i);
                    
                    if(!tabelaDeSimbolosGlobal.existeSimbolo(nomePar) && tabelaDeSimbolosProcedimento.existeSimbolo(nomePar))
                    {
                        tabelaDeSimbolosProcedimento.adicionarSimbolo(nomePar, tipoPar, null);
                        listaPar.add(tipoPar);
                    }
                    else
                    {
                        out.println("Linha : identificador "+nomePar+" ja declarado anteriormente");
                    }
                }
                tabelaDeSimbolosGlobal.adicionarSimbolo(nome, "procedimento", listaPar);
                   
                pilhaDeTabelas.empilhar(tabelaDeSimbolosProcedimento);  

            }
        }
        else 
            out.println("Linha : identificador "+nome+" ja declarado anteriormente");
        
        //ERRO 6
	if (ctx.comandos().contemRetorne == true){ //Verifica se existe um retorne dentro de uma declaracao global
            if (ctx.getStart().getText().equals("procedimento")){ //Verifica se o que chama o retorne nao eh uma funcao, ou seja, um procedimento
                int linha = ctx.comandos().stop.getLine(); // Como todos os retornes sao utilizados no fim do comandos(), entao isso eh valido
                out.println("Linha "+linha+": comando retorne nao permitido nesse escopo");
            }
        }
            
    }

    @Override
    public void enterCorpo(LAParser.CorpoContext ctx){
        //ERRO 6 TAMBEM
        if (ctx.comandos().contemRetorne == true){ //Verifica se existe um retorne aqui, se existir foi utilizado um retorne na funcao principal
            int linha = ctx.comandos().stop.getLine(); // Como todos os retornes sao utilizados no fim do comandos(), entao isso eh valido
            out.println("Linha "+linha+": comando retorne nao permitido nesse escopo");
        }
    }
    
    private String detectarTipo(LAParser.ExpressaoContext ctx) {
       // String tipo = ctx.nome_par; //descobrir como atribuir lista
        //atribuir nome_par a uma lista
        //percorrer essa lista na tabela de simbolos e recuperar os tipos de cada nome
         String tipo = null;
         TabelaDeSimbolos tabelaDeSimbolosAtual2 = pilhaDeTabelas.topo();
        
        for (String nome : ctx.nome_par)
        {
            if (nome != null)
            {
                
                //ctx.tipo_par.add(tipo);
            }
        }
        return tipo;
    }
    
    @Override
    public void enterArgumentos_opcional(LAParser.Argumentos_opcionalContext ctx)
    {
        //percorrer a lista de nomes 
    }
}
