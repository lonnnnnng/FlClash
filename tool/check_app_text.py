"""作者 long：中英文资源必须成对且参数一致，防止后续新增提示漏译或破坏动态字段。"""
from pathlib import Path
import hashlib
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def read_resources(locale):
    path = ROOT / 'android/common/src/main/res' / locale / 'app_text.xml'
    rows = ET.parse(path).getroot().findall('string')
    result = {row.attrib['name']: row.text or '' for row in rows}
    assert len(result) == len(rows), f'Duplicate resource keys in {path}'
    return result


def main():
    english, chinese = read_resources('values'), read_resources('values-zh')
    assert english.keys() == chinese.keys(), 'Chinese and English resource keys differ'
    for key, value in english.items():
        assert sorted(re.findall(r'\{\d+\}', value)) == sorted(re.findall(r'\{\d+\}', chinese[key])), key
        # 作者 long：语言选项“中文”保留自称，其他英文资源不能以中文原文冒充已翻译。
        native_name = 'text_' + hashlib.sha256('中文'.encode()).hexdigest()[:12]
        if key != native_name:
            assert not re.search(r'[\u4e00-\u9fff]', value), f'Untranslated English resource: {key}'
    registry = (ROOT / 'android/common/src/main/java/com/lonnnnnng/flclash/common/AppTextCatalog.kt').read_text()
    assert set(re.findall(r'R.string.(text_[0-9a-f]+)', registry)) == english.keys(), 'Resource registry differs'
    print(f'{len(english)} bilingual resource pairs: keys, placeholders and English coverage passed')


if __name__ == '__main__':
    main()
