import json

def process_files():
    # 读取 Localizable_bak.xcstrings
    with open('iosApp/Localizable_bak.xcstrings', 'r', encoding='utf-8') as f:
        bak_data = json.load(f)

    # 读取 Localizable.xcstrings
    with open('iosApp/Localizable.xcstrings', 'r', encoding='utf-8') as f:
        main_data = json.load(f)

    # 获取 bak 文件中的所有 key
    bak_keys = set(bak_data.get('strings', {}).keys())
    
    # 统计新添加的key数量
    added_count = 0
    
    # 遍历 main_data 中的所有 key
    for key, value in main_data.get('strings', {}).items():
        # 如果这个 key 不在 bak 文件中，就添加它
        if key not in bak_keys:
            bak_data['strings'][key] = value
            added_count += 1

    # 打印统计信息
    print(f"原始文件中的key数量: {len(bak_keys)}")
    print(f"新文件中的key数量: {len(main_data.get('strings', {}))}")
    print(f"新添加的key数量: {added_count}")
    
    # 保存更新后的 bak 文件
    with open('iosApp/Localizable_bak_merged.xcstrings', 'w', encoding='utf-8') as f:
        json.dump(bak_data, f, ensure_ascii=False, indent=2)

if __name__ == '__main__':
    process_files() 