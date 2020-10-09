package org.ansj.splitWord.analysis;

import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.domain.TermNature;
import org.ansj.domain.TermNatures;
import org.ansj.recognition.arrimpl.NumRecognition;
import org.ansj.recognition.arrimpl.PersonRecognition;
import org.ansj.splitWord.Analysis;
import org.ansj.util.AnsjReader;
import org.ansj.util.Graph;
import org.ansj.util.TermUtil;
import org.ansj.util.TermUtil.InsertTermType;
import org.nlpcn.commons.lang.tire.GetWord;
import org.nlpcn.commons.lang.tire.domain.Forest;
import org.nlpcn.commons.lang.util.ObjConver;
import org.nlpcn.commons.lang.util.StringUtil;

import java.io.Reader;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 用于检索的分词方式
 *
 * @author ansj
 *
 */
public class IndexAnalysis extends Analysis {

    public static final String POS_ADMINISTRATIVE_DIVISION = "ns";

    @Override
    protected List<Term> getResult(final Graph graph) {
        Merger merger = new Merger() {
            @Override
            public List<Term> merger() {
                // 用户自定义词典的识别
                userDefineRecognition(graph, forests);
                graph.walkPath();
                // 数字发现
                if (isNumRecognition) {
                    new NumRecognition(isQuantifierRecognition).recognition(graph);
                }
                // 姓名识别
                if (graph.hasPerson && isNameRecognition) {
                    // 人名识别
                    new PersonRecognition().recognition(graph);
                    graph.walkPathByScore();
                    graph.walkPathByScore();
                }
                return result();
            }

            private void userDefineRecognition(final Graph graph, Forest... forests) {

                if (forests == null) {
                    return;
                }

                int beginOff = graph.terms[0].getOffe();

                Forest forest = null;
                for (int i = forests.length - 1; i >= 0; i--) {
                    forest = forests[i];
                    if (forest == null) {
                        continue;
                    }

                    GetWord word = forest.getWord(graph.chars);
                    String temp = null;
                    int tempFreq = 50;
                    while ((temp = word.getFrontWords()) != null) {
                        Term tempTerm = graph.terms[word.offe];
                        tempFreq = getInt(word.getParam()[1], 50);
                        if (graph.terms[word.offe] != null && graph.terms[word.offe].getName().equals(temp)) {
                            TermNatures termNatures = new TermNatures(new TermNature(word.getParam()[0], tempFreq), tempFreq, -1);
                            tempTerm.updateTermNaturesAndNature(termNatures);
                        } else {
                            Term term = new Term(temp, beginOff + word.offe, word.getParam()[0], tempFreq);
                            term.selfScore(-1 * Math.pow(Math.log(tempFreq), temp.length()));
                            TermUtil.insertTerm(graph.terms, term, InsertTermType.REPLACE);
                        }
                    }
                }

                graph.rmLittlePath();
                graph.walkPathByScore();
                graph.rmLittlePath();
            }

            private int getInt(String str, int def) {
                try {
                    return Integer.parseInt(str);
                } catch (NumberFormatException e) {
                    return def;
                }
            }

            /**
             * 检索的分词
             *
             * @return
             */
            private List<Term> result() {
                String temp = null;
                Set<String> set = new HashSet<String>();
                List<Term> result = new LinkedList<Term>();
                int length = graph.terms.length - 1;
                for (int i = 0; i < length; i++) {
                    if (graph.terms[i] != null) {
                        setIsNewWord(graph.terms[i]);
                        result.add(graph.terms[i]);
                        set.add(graph.terms[i].getName() + graph.terms[i].getOffe());
                    }
                }
                setRealName(graph, result);
                LinkedList<Term> last = new LinkedList<Term>();
                if (forests != null) {
                    for (Forest forest : forests) {
                        if (forest == null) {
                            continue;
                        }
                        for (Term term : result) {
                            String name = term.getName();
                            // 若词语长度小于3，则不再切分
                            if (null == term || StringUtil.isBlank(name) || name.length() < 3) {
                                continue;
                            }

                            String pos = term.getNatureStr();
                            GetWord word = forest.getWord(name);
                            while ((temp = word.getAllWords()) != null) {
                                if (StringUtil.isBlank(temp)) {
                                    continue;
                                }
                                // 针对于中国行政区划词，只保留完整名称与删除后缀的词语。
                                // e.g.  江苏省 =》江苏省，江苏
                                // 若词库中有 苏省，将会被丢弃
                                if (StringUtil.isNotBlank(pos) && pos.equals(POS_ADMINISTRATIVE_DIVISION)) {
                                    if (!(name.substring(0, name.length() - 1).equals(temp) || name.equals(temp))) {
                                        continue;
                                    }
                                }
                                int offset = name.indexOf(temp);
                                if (offset == -1) {
                                    continue;
                                }
                                String nature = word.getParam(0);
                                offset = term.getOffe() + offset;
                                if (!set.contains(temp + offset)) {
                                    set.add(temp + offset);
                                    last.add(new Term(temp, offset, nature, ObjConver.getIntValue(word.getParam(1))));
                                }
                            }
                        }
                    }
                }

                result.addAll(last);
                Collections.sort(result, new Comparator<Term>() {
                    @Override
                    public int compare(Term o1, Term o2) {
                        if (o1.getOffe() == o2.getOffe()) {
                            return o2.getName().length() - o1.getName().length();
                        } else {
                            return o1.getOffe() - o2.getOffe();
                        }
                    }
                });

                setRealName(graph, result);
                return result;
            }
        };

        return merger.merger();
    }

    public IndexAnalysis() {
        super();
    }

    public IndexAnalysis(Reader reader) {
        super.resetContent(new AnsjReader(reader));
    }

    public static Result parse(String str) {
        return new IndexAnalysis().parseStr(str);
    }

    public static Result parse(String str, Forest... forests) {
        return new IndexAnalysis().setForests(forests).parseStr(str);
    }

}
